package com.redxela.processing;

import static com.redxela.domain.ServiceLevel.ECONOMY;
import static com.redxela.domain.ServiceLevel.EXPRESS;
import static com.redxela.testsupport.TestOrders.HEAVY_PRODUCT;
import static com.redxela.testsupport.TestOrders.STANDARD_PRODUCT;
import static org.assertj.core.api.Assertions.assertThat;

import com.redxela.catalog.ProductCatalog;
import com.redxela.domain.Customer;
import com.redxela.domain.Order;
import com.redxela.domain.OrderLine;
import com.redxela.domain.ServiceLevel;
import com.redxela.inventory.Inventory;
import com.redxela.inventory.MissingStock;
import com.redxela.inventory.StockSnapshot;
import com.redxela.resources.ResourceRegistry;
import com.redxela.sim.SimClock;
import com.redxela.testsupport.Await;
import com.redxela.testsupport.TestOrders;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * El despachador y el stock. El inventario empieza vacío y cada test recibe solo lo que necesita; con
 * el reloj pausado los pedidos admitidos quedan congelados reteniendo su reserva.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class DispatcherStockTest {

    private static final String SHIRT = STANDARD_PRODUCT;
    private static final String FRIDGE = HEAVY_PRODUCT;

    private final SimClock clock = new SimClock();
    private final ResourceRegistry resources = new ResourceRegistry();
    private final Inventory inventory = new Inventory(ProductCatalog.defaultCatalog(), 0);
    private OrderProcessor processor;
    private OrderScheduler scheduler;

    @BeforeEach
    void setUp() {
        clock.pause();
        OrderWorkflow workflow = new OrderWorkflow(resources, clock);
        processor = new OrderProcessor(workflow, ProcessingTimes.defaults());
        scheduler = new OrderScheduler(clock, resources, workflow, processor, inventory);
        scheduler.start();
    }

    @AfterEach
    void tearDown() {
        scheduler.close();
        processor.close();
    }

    private void enqueue(String id, ServiceLevel level, String productId, int units) {
        scheduler.enqueue(TestOrders.order(id, level, productId, units));
    }

    private Set<String> activeIds() {
        return processor.activeOrders(clock.now()).stream().map(OrderProgressSnapshot::orderId).collect(Collectors.toSet());
    }

    private StockSnapshot stockOf(String productId) {
        return inventory.snapshot().stream().filter(stock -> stock.productId().equals(productId)).findFirst().orElseThrow();
    }

    @Test
    void an_order_without_stock_does_not_start_even_with_free_workers() throws Exception {
        enqueue("O-1", EXPRESS, SHIRT, 5);

        Await.until("order listed as waiting", () -> scheduler.snapshot(clock.now()).waitingTotal() == 1);
        Thread.sleep(150); // da tiempo a que el despachador la hubiera admitido si lo fuera a hacer

        assertThat(activeIds()).isEmpty();
        assertThat(scheduler.snapshot(clock.now()).waiting().get(0).missingStock())
                .containsExactly(new MissingStock(SHIRT, 5));
    }

    @Test
    void skips_an_order_lacking_stock_and_serves_the_next_best_one() throws Exception {
        inventory.receive(FRIDGE, 50);
        enqueue("express-without-stock", EXPRESS, SHIRT, 5);   // la mejor por prioridad, pero no hay camisetas
        enqueue("economy-with-stock", ECONOMY, FRIDGE, 5);

        Await.until("the order with stock is admitted", () -> activeIds().contains("economy-with-stock"));

        assertThat(activeIds()).containsExactly("economy-with-stock");
        assertThat(scheduler.snapshot(clock.now()).waiting()).extracting(WaitingOrderSnapshot::orderId)
                .containsExactly("express-without-stock");
    }

    @Test
    void a_stock_change_wakes_up_the_dispatcher_and_admits_the_waiting_order() throws Exception {
        enqueue("O-1", EXPRESS, SHIRT, 5);
        Await.until("order waiting", () -> scheduler.snapshot(clock.now()).waitingTotal() == 1);

        inventory.receive(SHIRT, 5);
        scheduler.stockChanged(); // lo que Inventario avisa tras procesar una llegada

        Await.until("order admitted", () -> activeIds().contains("O-1"));
        assertThat(scheduler.snapshot(clock.now()).waitingTotal()).isZero();
    }

    @Test
    void admitting_an_order_reserves_all_of_its_stock() throws Exception {
        inventory.receive(SHIRT, 20);
        enqueue("O-1", EXPRESS, SHIRT, 8);

        Await.until("order admitted", () -> activeIds().contains("O-1"));

        assertThat(stockOf(SHIRT)).isEqualTo(new StockSnapshot(SHIRT, 12, 8));
    }

    @Test
    void an_order_is_not_admitted_if_even_one_of_its_lines_lacks_stock_and_nothing_is_reserved() throws Exception {
        inventory.receive(SHIRT, 50); // hay camisetas pero no refrigeradoras
        Customer customer = new Customer("C", "Cliente", EXPRESS);
        scheduler.enqueue(new Order("mixed", customer, List.of(
                new OrderLine(TestOrders.product(SHIRT), 5), new OrderLine(TestOrders.product(FRIDGE), 1))));

        Await.until("order waiting", () -> scheduler.snapshot(clock.now()).waitingTotal() == 1);
        Thread.sleep(150);

        assertThat(activeIds()).isEmpty();
        assertThat(stockOf(SHIRT)).isEqualTo(new StockSnapshot(SHIRT, 50, 0)); // sin stock parcial retenido
        assertThat(scheduler.snapshot(clock.now()).waiting().get(0).missingStock())
                .containsExactly(new MissingStock(FRIDGE, 1));
    }

    @Test
    void never_oversells_when_many_orders_compete_for_limited_stock() throws Exception {
        inventory.receive(SHIRT, 55);
        for (int i = 0; i < 10; i++) {
            enqueue("O-" + i, EXPRESS, SHIRT, 10);
        }

        Await.until("five orders admitted", () -> activeIds().size() == 5);
        Thread.sleep(150); // un sexto pedido no puede entrar: quedan 5 unidades y necesita 10

        assertThat(activeIds()).hasSize(5);
        assertThat(stockOf(SHIRT)).isEqualTo(new StockSnapshot(SHIRT, 5, 50));
        assertThat(scheduler.snapshot(clock.now()).waitingTotal()).isEqualTo(5);
    }

    @Test
    void returns_the_reservation_when_the_processor_is_closed_and_the_order_cannot_start() throws Exception {
        inventory.receive(SHIRT, 20);
        processor.close();

        enqueue("O-1", EXPRESS, SHIRT, 8);

        Await.until("dispatcher gave up", () -> !dispatcherAlive());
        assertThat(stockOf(SHIRT)).isEqualTo(new StockSnapshot(SHIRT, 20, 0)); // la reserva volvió
        assertThat(scheduler.snapshot(clock.now()).waitingTotal()).isEqualTo(1); // y el pedido no se perdió
    }

    private static boolean dispatcherAlive() {
        return Thread.getAllStackTraces().keySet().stream()
                .anyMatch(thread -> thread.getName().equals("dispatcher") && thread.isAlive());
    }
}
