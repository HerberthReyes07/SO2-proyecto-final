package com.redxela.processing;

import static com.redxela.testsupport.TestOrders.HEAVY_PRODUCT;
import static com.redxela.testsupport.TestOrders.STANDARD_PRODUCT;
import static org.assertj.core.api.Assertions.assertThat;

import com.redxela.domain.Order;
import com.redxela.domain.OrderStatus;
import com.redxela.domain.ResourceType;
import com.redxela.domain.Stage;
import com.redxela.resources.PoolSnapshot;
import com.redxela.resources.ResourceRegistry;
import com.redxela.sim.SimClock;
import com.redxela.testsupport.Await;
import com.redxela.testsupport.FakeOperation;
import com.redxela.testsupport.TestOrders;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Pruebas deterministas del flujo de un pedido. Truco central: pausar el reloj congela al pedido
 * en su primer {@code sleep}, y llenar un pool de operaciones falsas lo deja esperando ahí, de modo
 * que se puede observar el estado intermedio sin depender de tiempos.
 */
@Timeout(value = 20, unit = TimeUnit.SECONDS)
class OrderWorkflowTest {

    private final ResourceRegistry registry = new ResourceRegistry();
    private final SimClock clock = new SimClock();
    private final OrderWorkflow workflow = new OrderWorkflow(registry, clock);
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @BeforeEach
    void runClockAtMaximumSpeed() {
        clock.setSpeed(SimClock.MAX_SPEED);
    }

    @AfterEach
    void shutdownThreads() {
        executor.shutdownNow();
    }

    @Test
    void completes_the_order_and_releases_every_resource() throws Exception {
        OrderExecution execution = execution(TestOrders.order("O-1", HEAVY_PRODUCT, 3));

        workflow.run(execution);

        assertThat(execution.order().status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(execution.progress(clock.now()).progress()).isEqualTo(1.0);
        assertNothingIsHeld();
    }

    @Test
    void holds_the_worker_and_the_forklift_while_picking_a_heavy_order() throws Exception {
        clock.pause();
        OrderExecution execution = execution(TestOrders.order("O-1", HEAVY_PRODUCT, 3));

        Future<?> running = start(execution);
        Await.until("picking in progress", () -> {
            OrderProgressSnapshot snapshot = progressOf(execution);
            return snapshot.held().equals(List.of(ResourceType.WAREHOUSE_WORKER, ResourceType.FORKLIFT))
                    && snapshot.awaiting() == null;
        });

        assertThat(progressOf(execution).stage()).isEqualTo(Stage.PICKING);
        assertThat(execution.order().status()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(holdersOf(ResourceType.FORKLIFT)).containsExactly("O-1");
        assertThat(holdersOf(ResourceType.WAREHOUSE_WORKER)).containsExactly("O-1");

        clock.resume();
        running.get();
        assertNothingIsHeld();
    }

    @Test
    void keeps_the_previous_stage_resource_until_the_next_one_is_acquired() throws Exception {
        // Se ocupan las 6 estaciones de empaque: el pedido pesado llegará a empaque y se quedará esperando.
        List<FakeOperation> blockers = occupyAll(ResourceType.PACKING_STATION);
        OrderExecution execution = execution(TestOrders.order("O-1", HEAVY_PRODUCT, 3));

        Future<?> running = start(execution);
        Await.until("waiting for a packing station", () -> progressOf(execution).awaiting() == ResourceType.PACKING_STATION);

        // Traspaso: mientras espera empaque todavía retiene el montacargas.
        assertThat(progressOf(execution).held()).containsExactly(ResourceType.WAREHOUSE_WORKER, ResourceType.FORKLIFT);
        assertThat(holdersOf(ResourceType.FORKLIFT)).containsExactly("O-1");

        clock.pause(); // congelar el pedido en cuanto empiece a empacar
        registry.pool(ResourceType.PACKING_STATION).release(blockers.get(0));
        Await.until("forklift handed over", () -> holdersOf(ResourceType.FORKLIFT).isEmpty());

        assertThat(progressOf(execution).held()).containsExactly(ResourceType.WAREHOUSE_WORKER, ResourceType.PACKING_STATION);
        assertThat(progressOf(execution).stage()).isEqualTo(Stage.PACKING);

        clock.resume();
        running.get();
        releaseAll(ResourceType.PACKING_STATION, blockers.subList(1, blockers.size()));
        assertNothingIsHeld();
    }

    @Test
    void waits_in_the_waiting_state_when_every_worker_is_busy() throws Exception {
        List<FakeOperation> blockers = occupyAll(ResourceType.WAREHOUSE_WORKER);
        OrderExecution execution = execution(TestOrders.order("O-1", STANDARD_PRODUCT, 2));

        Future<?> running = start(execution);
        Await.until("waiting for a worker", () -> progressOf(execution).awaiting() == ResourceType.WAREHOUSE_WORKER);

        assertThat(execution.order().status()).isEqualTo(OrderStatus.WAITING);
        assertThat(progressOf(execution).held()).isEmpty();

        registry.pool(ResourceType.WAREHOUSE_WORKER).release(blockers.get(0));
        running.get();
        assertThat(execution.order().status()).isEqualTo(OrderStatus.COMPLETED);
        releaseAll(ResourceType.WAREHOUSE_WORKER, blockers.subList(1, blockers.size()));
    }

    @Test
    void releases_everything_when_interrupted_in_the_middle_of_the_work() throws Exception {
        clock.pause();
        OrderExecution execution = execution(TestOrders.order("O-1", HEAVY_PRODUCT, 3));
        Future<?> running = start(execution);
        Await.until("working", () -> holdersOf(ResourceType.FORKLIFT).contains("O-1") && progressOf(execution).awaiting() == null);

        running.cancel(true); // interrumpe el hilo del pedido

        Await.until("everything released", this::nothingIsHeld);
        assertThat(execution.order().status()).isEqualTo(OrderStatus.PROCESSING); // nunca llegó a completarse
    }

    // ---- Utilidades ----

    private OrderExecution execution(Order order) {
        return new OrderExecution(order, ExecutionPlan.forOrder(order, ProcessingTimes.defaults()), 1);
    }

    private Future<?> start(OrderExecution execution) {
        return executor.submit(() -> {
            workflow.run(execution);
            return null;
        });
    }

    private OrderProgressSnapshot progressOf(OrderExecution execution) {
        return execution.progress(clock.now());
    }

    private List<String> holdersOf(ResourceType type) {
        return registry.pool(type).snapshot().holders();
    }

    /** Ocupa todas las unidades del pool con operaciones falsas. */
    private List<FakeOperation> occupyAll(ResourceType type) throws InterruptedException, com.redxela.resources.OperationPreemptedException {
        List<FakeOperation> blockers = new ArrayList<>();
        for (int i = 0; i < type.capacity(); i++) {
            FakeOperation blocker = new FakeOperation("blocker-" + i);
            registry.pool(type).acquire(blocker);
            blockers.add(blocker);
        }
        return blockers;
    }

    private void releaseAll(ResourceType type, List<FakeOperation> blockers) {
        blockers.forEach(blocker -> registry.pool(type).release(blocker));
    }

    private boolean nothingIsHeld() {
        return registry.snapshots().stream().allMatch(pool -> pool.holders().isEmpty());
    }

    private void assertNothingIsHeld() {
        for (PoolSnapshot pool : registry.snapshots()) {
            assertThat(pool.holders()).as("holders of %s", pool.type()).isEmpty();
        }
    }
}
