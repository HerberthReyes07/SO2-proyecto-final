package com.redxela.processing;

import static com.redxela.testsupport.TestOrders.STANDARD_PRODUCT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.redxela.catalog.ProductCatalog;
import com.redxela.domain.CompletedOrder;
import com.redxela.domain.Order;
import com.redxela.domain.ServiceLevel;
import com.redxela.inventory.Inventory;
import com.redxela.inventory.StockSnapshot;
import com.redxela.resources.ResourceRegistry;
import com.redxela.sim.SimClock;
import com.redxela.testsupport.Await;
import com.redxela.testsupport.TestOrders;
import com.redxela.warehouse.PlacementStrategy;
import com.redxela.warehouse.Warehouse;
import com.redxela.warehouse.WarehouseLayout;
import com.redxela.warehouse.LotSnapshot;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Verifica que el stock reservado salga al despachar el pedido y vuelva si el pedido se aborta. */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class StockHandlingTest {

    private final SimClock clock = new SimClock();
    private final ResourceRegistry resources = new ResourceRegistry();
    private final ProductCatalog catalog = ProductCatalog.defaultCatalog();
    private final Inventory inventory = new Inventory(catalog, 0);
    private final Warehouse warehouse = new Warehouse(WarehouseLayout.defaults(), catalog, PlacementStrategy.FIRST_FREE);
    private final OrderWorkflow workflow = new OrderWorkflow(resources, clock);
    private final OrderProcessor processor = new OrderProcessor(workflow, ProcessingTimes.defaults());
    private long arrivals;
    private int lots;

    @AfterEach
    void tearDown() {
        processor.close();
    }

    /** Recibe mercadería como lo hace Inventario: primero el almacén y después el inventario. */
    private void receiveShirts(int units) {
        warehouse.store("LOT-" + (++lots), STANDARD_PRODUCT, units);
        inventory.receive(STANDARD_PRODUCT, units);
    }

    /** Unidades de camisetas que hay físicamente en el almacén. */
    private int shirtsInWarehouse() {
        return warehouse.snapshot().lots().stream()
                .filter(lot -> lot.productId().equals(STANDARD_PRODUCT))
                .mapToInt(LotSnapshot::units).sum();
    }

    private StockSnapshot shirts() {
        return inventory.snapshot().stream().filter(stock -> stock.productId().equals(STANDARD_PRODUCT)).findFirst().orElseThrow();
    }

    /** Reserva el stock del pedido y lo admite, como lo haría el despachador. */
    private void reserveAndAdmit(Order order) throws Exception {
        assertThat(inventory.reserve(order)).isTrue();
        OrderExecution execution = processor.newExecution(TestOrders.queued(order, ++arrivals));
        workflow.assignWorker(execution);
        processor.start(execution);
    }

    @Test
    void completing_an_order_removes_its_units_from_the_inventory() {
        receiveShirts(10);
        inventory.reserve(TestOrders.order("O-1", STANDARD_PRODUCT, 4));

        new StockHandling(inventory, warehouse).onCompleted(
                new CompletedOrder("O-1", ServiceLevel.STANDARD, 4, Duration.ZERO, Duration.ofMinutes(5)));

        assertThat(shirts()).isEqualTo(new StockSnapshot(STANDARD_PRODUCT, 6, 0));
        assertThat(shirtsInWarehouse()).isEqualTo(6); // y también salieron físicamente del almacén
    }

    @Test
    void aborting_an_order_returns_its_units_and_tolerates_an_order_without_reservation() {
        receiveShirts(10);
        inventory.reserve(TestOrders.order("O-1", STANDARD_PRODUCT, 4));
        StockHandling handling = new StockHandling(inventory, warehouse);

        handling.onAborted("O-1");
        handling.onAborted("O-404");

        assertThat(shirts()).isEqualTo(new StockSnapshot(STANDARD_PRODUCT, 10, 0));
        assertThat(shirtsInWarehouse()).isEqualTo(10); // abortar nunca mueve mercadería: solo estaba apartada
    }

    @Test
    void completing_an_order_that_never_reserved_is_reported_as_an_error() {
        StockHandling handling = new StockHandling(inventory, warehouse);

        assertThatThrownBy(() -> handling.onCompleted(
                new CompletedOrder("O-404", ServiceLevel.STANDARD, 1, Duration.ZERO, Duration.ZERO)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void a_dispatched_order_consumes_its_stock_through_the_processor() throws Exception {
        clock.setSpeed(SimClock.MAX_SPEED);
        processor.addListener(new StockHandling(inventory, warehouse));
        receiveShirts(30);

        reserveAndAdmit(TestOrders.order("O-1", STANDARD_PRODUCT, 7));

        Await.until("order completed", () -> processor.completedCount() == 1);
        Await.until("stock consumed", () -> shirts().reserved() == 0);
        assertThat(shirts()).isEqualTo(new StockSnapshot(STANDARD_PRODUCT, 23, 0));
        assertThat(shirtsInWarehouse()).isEqualTo(23);
    }

    @Test
    void an_interrupted_order_returns_its_stock_through_the_processor() throws Exception {
        clock.pause();
        processor.addListener(new StockHandling(inventory, warehouse));
        receiveShirts(30);
        reserveAndAdmit(TestOrders.order("O-1", STANDARD_PRODUCT, 7));
        Await.until("order working", () -> shirts().reserved() == 7
                && !processor.activeOrders(clock.now()).isEmpty());

        processor.close();

        Await.until("stock back to available", () -> shirts().equals(new StockSnapshot(STANDARD_PRODUCT, 30, 0)));
        assertThat(shirtsInWarehouse()).isEqualTo(30);
    }
}
