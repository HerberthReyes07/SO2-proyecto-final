package com.redxela.processing;

import static com.redxela.testsupport.TestOrders.HEAVY_PRODUCT;
import static org.assertj.core.api.Assertions.assertThat;

import com.redxela.catalog.ProductCatalog;
import com.redxela.domain.Order;
import com.redxela.domain.OrderStatus;
import com.redxela.domain.QueuedOrder;
import com.redxela.domain.ResourceType;
import com.redxela.inventory.Inventory;
import com.redxela.inventory.StockSnapshot;
import com.redxela.resources.ResourceRegistry;
import com.redxela.sim.SimClock;
import com.redxela.testsupport.Await;
import com.redxela.testsupport.FakeOperation;
import com.redxela.testsupport.TestOrders;
import com.redxela.warehouse.PlacementStrategy;
import com.redxela.warehouse.Warehouse;
import com.redxela.warehouse.WarehouseLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Qué pasa con un pedido que es elegido como víctima de un deadlock: libera todo, devuelve su stock y
 * vuelve a la lista de espera, y luego se atiende de nuevo hasta despacharse.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class OrderExpulsionTest {

    private final SimClock clock = new SimClock();
    private final ResourceRegistry registry = new ResourceRegistry();
    private final ProductCatalog catalog = ProductCatalog.defaultCatalog();
    private final Inventory inventory = new Inventory(catalog, 0);
    private final Warehouse warehouse = new Warehouse(WarehouseLayout.defaults(), catalog, PlacementStrategy.FIRST_FREE);
    private final OrderWorkflow workflow = new OrderWorkflow(registry, clock);
    private final OrderProcessor processor = new OrderProcessor(workflow, ProcessingTimes.defaults());
    private final OrderScheduler scheduler = new OrderScheduler(clock, registry, workflow, processor, inventory);
    private final List<String> aborted = Collections.synchronizedList(new ArrayList<>());
    private final List<QueuedOrder> expelled = Collections.synchronizedList(new ArrayList<>());
    private final List<FakeOperation> blockers = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        clock.setSpeed(SimClock.MAX_SPEED);
        warehouse.store("INIT", HEAVY_PRODUCT, 50);
        inventory.receive(HEAVY_PRODUCT, 50);
        processor.addListener(new StockHandling(inventory, warehouse));
        processor.addListener(new OrderLifecycleListener() {
            @Override
            public void onAborted(String orderId) {
                aborted.add(orderId);
            }
        });
        // Se ocupan las 6 estaciones de empaque: el pedido pesado se quedará esperando una.
        for (int i = 0; i < ResourceType.PACKING_STATION.capacity(); i++) {
            FakeOperation blocker = new FakeOperation("busy-" + i);
            registry.pool(ResourceType.PACKING_STATION).acquire(blocker);
            blockers.add(blocker);
        }
    }

    @AfterEach
    void tearDown() {
        scheduler.close();
        processor.close();
    }

    private StockSnapshot fridges() {
        return inventory.snapshot().stream().filter(stock -> stock.productId().equals(HEAVY_PRODUCT)).findFirst().orElseThrow();
    }

    /** Reserva el stock y admite el pedido a mano; devuelve su ejecución. */
    private OrderExecution admit(Order order, QueuedOrder queued) throws Exception {
        assertThat(inventory.reserve(order)).isTrue();
        OrderExecution execution = processor.newExecution(queued);
        workflow.assignWorker(execution);
        processor.start(execution);
        return execution;
    }

    @Test
    void an_expelled_order_releases_everything_returns_its_stock_and_goes_back_to_the_waiting_list() throws Exception {
        processor.onExpelled(expelled::add);
        Order order = TestOrders.order("O-1", HEAVY_PRODUCT, 3);
        QueuedOrder queued = new QueuedOrder(order, java.time.Duration.ofMinutes(4), 9);
        OrderExecution execution = admit(order, queued);
        Await.until("waiting for a packing station", () -> execution.progress(clock.now()).awaiting() == ResourceType.PACKING_STATION);
        assertThat(fridges()).isEqualTo(new StockSnapshot(HEAVY_PRODUCT, 47, 3));

        assertThat(registry.pool(ResourceType.PACKING_STATION).preempt(execution)).isTrue();

        Await.until("order handed back", () -> !expelled.isEmpty());
        assertThat(expelled.get(0)).isEqualTo(queued); // el mismo registro: conserva su hora y número de llegada
        assertThat(order.status()).isEqualTo(OrderStatus.WAITING);
        assertThat(aborted).containsExactly("O-1");
        Await.until("order no longer in process", () -> processor.activeOrders(clock.now()).isEmpty());
        assertThat(fridges()).isEqualTo(new StockSnapshot(HEAVY_PRODUCT, 50, 0)); // devolvió su stock reservado
        // No retiene nada: ni el montacargas de picking, ni el encargado.
        assertThat(registry.pool(ResourceType.FORKLIFT).snapshot().holders()).isEmpty();
        assertThat(registry.pool(ResourceType.WAREHOUSE_WORKER).snapshot().holders()).isEmpty();
        assertThat(processor.completedCount()).isZero();
    }

    @Test
    void when_returned_through_the_scheduler_the_order_is_admitted_again_counts_a_retry_and_is_eventually_dispatched()
            throws Exception {
        processor.onExpelled(scheduler::requeue);
        scheduler.start();
        scheduler.enqueue(TestOrders.order("O-1", HEAVY_PRODUCT, 3));
        Await.until("admitted and waiting for a packing station", () -> processor.activeOrders(clock.now()).stream()
                .anyMatch(order -> order.awaiting() == ResourceType.PACKING_STATION));
        OrderExecution first = (OrderExecution) registry.pool(ResourceType.PACKING_STATION).operations().waiting().get(0);

        assertThat(registry.pool(ResourceType.PACKING_STATION).preempt(first)).isTrue();

        // El despachador la vuelve a admitir (con un reintento) y vuelve a quedar esperando la estación ocupada.
        Await.until("second attempt waiting again", () -> processor.activeOrders(clock.now()).stream()
                .anyMatch(order -> order.retries() == 1 && order.awaiting() == ResourceType.PACKING_STATION));
        assertThat(fridges().reserved()).as("only one reservation exists, not two").isEqualTo(3);

        blockers.forEach(blocker -> registry.pool(ResourceType.PACKING_STATION).release(blocker));
        Await.until("dispatched", () -> processor.completedCount() == 1);
        Await.until("stock consumed once", () -> fridges().reserved() == 0);
        assertThat(fridges().available()).isEqualTo(47); // salieron 3 de 50, una sola vez
    }
}
