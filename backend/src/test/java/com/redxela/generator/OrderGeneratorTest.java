package com.redxela.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.redxela.catalog.ProductCatalog;
import com.redxela.customers.CustomerRegistry;
import com.redxela.orders.OrderLineRequest;
import com.redxela.sim.SimClock;
import com.redxela.testsupport.Await;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class OrderGeneratorTest {

    private record Placed(String customerId, List<OrderLineRequest> lines) {
    }

    private static final long SETTLE_MILLIS = 200;

    private final SimClock clock = new SimClock();
    private final List<Placed> placed = Collections.synchronizedList(new ArrayList<>());
    private OrderGenerator generator;

    @BeforeEach
    void setUp() {
        clock.setSpeed(SimClock.MAX_SPEED);
        generator = newGenerator(placed::add);
        generator.start();
    }

    @AfterEach
    void tearDown() {
        generator.close();
    }

    private OrderGenerator newGenerator(java.util.function.Consumer<Placed> sink) {
        CustomerRegistry customers = CustomerRegistry.withSampleCustomers();
        return new OrderGenerator(clock, WorkloadProfile.defaults(), ProductCatalog.defaultCatalog(),
                customers::all, (customerId, lines) -> sink.accept(new Placed(customerId, lines)));
    }

    @Test
    void does_not_generate_anything_until_it_is_enabled() throws Exception {
        Thread.sleep(SETTLE_MILLIS);

        assertThat(placed).isEmpty();
        assertThat(generator.snapshot().enabled()).isFalse();
    }

    @Test
    void generates_orders_once_enabled_and_counts_them() throws Exception {
        generator.configure(true, 5.0, 42L);

        Await.until("five orders", () -> placed.size() >= 5);
        Await.until("counter caught up", () -> generator.snapshot().generated() == placed.size());

        assertThat(generator.snapshot().enabled()).isTrue();
    }

    @Test
    void stops_generating_when_disabled() throws Exception {
        generator.configure(true, 5.0, 42L);
        Await.until("some orders", () -> placed.size() >= 3);

        generator.configure(false, null, null);
        Thread.sleep(SETTLE_MILLIS); // deja terminar un pedido que estuviera a punto de crearse
        int afterSettling = placed.size();
        Thread.sleep(SETTLE_MILLIS);

        assertThat(placed).hasSize(afterSettling);
    }

    @Test
    void does_not_generate_while_the_clock_is_paused_and_resumes_with_it() throws Exception {
        clock.pause();
        generator.configure(true, 5.0, 42L);
        Thread.sleep(SETTLE_MILLIS);
        assertThat(placed).isEmpty();

        clock.resume();

        Await.until("orders after resuming", () -> placed.size() >= 3);
    }

    @Test
    void the_same_seed_always_generates_the_same_orders() throws Exception {
        List<Placed> first = collectFirstOrders(42L, 15);
        List<Placed> second = collectFirstOrders(42L, 15);
        List<Placed> other = collectFirstOrders(43L, 15);

        assertThat(second).isEqualTo(first);
        assertThat(other).isNotEqualTo(first);
    }

    /** Reinicia la secuencia con una semilla y recoge los primeros pedidos que se generan. */
    private List<Placed> collectFirstOrders(long seed, int count) throws Exception {
        generator.configure(false, null, seed);
        Thread.sleep(SETTLE_MILLIS);
        placed.clear();
        generator.configure(true, 20.0, null);
        Await.until("first orders", () -> placed.size() >= count);
        generator.configure(false, null, null);
        synchronized (placed) {
            return new ArrayList<>(placed.subList(0, count));
        }
    }

    @Test
    void a_higher_intensity_takes_effect_without_waiting_for_the_interval_already_drawn() throws Exception {
        clock.setSpeed(100);
        long seed = 21L;
        // Con intensidad muy baja la primera llegada tardaría varios segundos reales; sin tajadas, subir
        // la intensidad no la adelantaría. Se comprueba que con esta semilla el trabajo sorteado es grande.
        double firstWork = new WorkloadGenerator(WorkloadProfile.defaults(), ProductCatalog.defaultCatalog(), seed)
                .nextArrivalWork();
        assertThat(firstWork).as("precondition: first drawn work for this seed").isGreaterThan(0.3);
        generator.configure(true, OrderGenerator.MIN_ORDERS_PER_MINUTE, seed);
        Thread.sleep(SETTLE_MILLIS);
        assertThat(placed).isEmpty();

        generator.configure(null, OrderGenerator.MAX_ORDERS_PER_MINUTE, null);

        Await.until("orders after raising the intensity", () -> placed.size() >= 3);
    }

    @Test
    void rejects_an_intensity_out_of_range_and_keeps_the_previous_settings() {
        GeneratorSnapshot before = generator.configure(false, 2.0, 5L);

        for (double invalid : new double[] {0, -1, OrderGenerator.MIN_ORDERS_PER_MINUTE / 2,
                OrderGenerator.MAX_ORDERS_PER_MINUTE + 1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThatThrownBy(() -> generator.configure(true, invalid, 99L))
                    .as("intensity %s", invalid)
                    .isInstanceOf(IllegalArgumentException.class);
        }

        assertThat(generator.snapshot()).isEqualTo(before);
    }

    @Test
    void the_snapshot_reflects_the_configuration() {
        generator.configure(false, 3.5, 1234L);

        GeneratorSnapshot snapshot = generator.snapshot();

        assertThat(snapshot.enabled()).isFalse();
        assertThat(snapshot.ordersPerMinute()).isEqualTo(3.5);
        assertThat(snapshot.seed()).isEqualTo(1234L);
    }

    @Test
    void keeps_generating_after_an_order_could_not_be_placed() throws Exception {
        generator.close();
        AtomicInteger attempts = new AtomicInteger();
        List<Placed> accepted = Collections.synchronizedList(new ArrayList<>());
        generator = newGenerator(order -> {
            if (attempts.incrementAndGet() <= 2) {
                throw new IllegalArgumentException("rejected on purpose");
            }
            accepted.add(order);
        });
        generator.start();

        generator.configure(true, 20.0, 42L);

        Await.until("orders accepted after the failures", () -> accepted.size() >= 3);
        assertThat(generator.snapshot().generated()).isGreaterThanOrEqualTo(3); // los rechazados no se cuentan
    }

    @Test
    void the_generator_thread_ends_when_closed() throws Exception {
        Await.until("generator running", OrderGeneratorTest::generatorThreadAlive);

        generator.close();

        Await.until("generator stopped", () -> !generatorThreadAlive());
    }

    private static boolean generatorThreadAlive() {
        return Thread.getAllStackTraces().keySet().stream()
                .anyMatch(thread -> thread.getName().equals("order-generator") && thread.isAlive());
    }
}
