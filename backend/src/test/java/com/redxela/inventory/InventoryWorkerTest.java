package com.redxela.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.redxela.catalog.ProductCatalog;
import com.redxela.sim.SimClock;
import com.redxela.testsupport.Await;
import com.redxela.warehouse.PlacementStrategy;
import com.redxela.warehouse.Warehouse;
import com.redxela.warehouse.WarehouseLayout;
import com.redxela.warehouse.WarehouseSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Pruebas de Inventario como consumidor. El reloj pausado congela al trabajador "a mitad de un
 * mensaje", lo que permite observar el buffer llenándose sin depender de tiempos.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class InventoryWorkerTest {

    private static final int BUFFER_CAPACITY = 2;

    private final SimClock clock = new SimClock();
    private final Inventory inventory = new Inventory(ProductCatalog.defaultCatalog(), 0);
    private final ProductCatalog catalog = ProductCatalog.defaultCatalog();
    private final Warehouse warehouse = new Warehouse(WarehouseLayout.defaults(), catalog, PlacementStrategy.FIRST_FREE);
    private final ReceptionBuffer buffer = new ReceptionBuffer(BUFFER_CAPACITY);
    private final List<String> notified = Collections.synchronizedList(new ArrayList<>());
    private final List<Integer> availableWhenNotified = Collections.synchronizedList(new ArrayList<>());
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private InventoryWorker worker;

    @BeforeEach
    void setUp() {
        clock.setSpeed(SimClock.MAX_SPEED);
        worker = newWorker(warehouse);
        worker.start();
    }

    private InventoryWorker newWorker(Warehouse target) {
        return new InventoryWorker(buffer, inventory, target, clock, InventoryTimes.defaults(), productId -> {
            notified.add(productId);
            availableWhenNotified.add(availableOf(productId));
        });
    }

    @AfterEach
    void tearDown() {
        worker.close();
        executor.shutdownNow();
    }

    private int availableOf(String productId) {
        return inventory.snapshot().stream().filter(stock -> stock.productId().equals(productId))
                .findFirst().orElseThrow().available();
    }

    @Test
    void adds_the_received_units_to_the_stock_and_then_announces_the_change() throws Exception {
        buffer.put(new ReceptionMessage("TRK-1", "P-001", 50));

        Await.until("stock updated", () -> availableOf("P-001") == 50);
        Await.until("change announced", () -> !notified.isEmpty());

        assertThat(notified).containsExactly("P-001");
        // Cuando se avisa, el stock ya está actualizado: quien reevalúa los pedidos verá las unidades nuevas.
        assertThat(availableWhenNotified).containsExactly(50);
        assertThat(worker.snapshot().processed()).isEqualTo(1);
    }

    @Test
    void stores_each_delivery_as_a_lot_in_the_warehouse() throws Exception {
        buffer.put(new ReceptionMessage("TRK-7", "P-001", 50));

        Await.until("stock updated", () -> availableOf("P-001") == 50);

        WarehouseSnapshot snapshot = warehouse.snapshot();
        assertThat(snapshot.lots()).hasSize(1);
        assertThat(snapshot.lots().get(0).lotId()).isEqualTo("LOT-TRK-7");
        assertThat(snapshot.lots().get(0).units()).isEqualTo(50);
        assertThat(snapshot.usedLocations()).isEqualTo(1);
    }

    @Test
    void stores_only_what_fits_and_adds_only_that_to_the_inventory_when_the_warehouse_is_nearly_full() throws Exception {
        worker.close();
        Warehouse tiny = new Warehouse(new WarehouseLayout(1, 1, 2, 1_000), catalog, PlacementStrategy.FIRST_FREE);
        worker = newWorker(tiny);
        worker.start();

        buffer.put(new ReceptionMessage("TRK-1", "P-001", 1_500)); // camisetas: caben 500 por ubicación, 2 ubicaciones

        Await.until("partial stock", () -> availableOf("P-001") == 1_000);
        assertThat(tiny.snapshot().rejectedUnits()).isEqualTo(500);
        assertThat(notified).containsExactly("P-001");
        Await.until("message counted as processed", () -> worker.snapshot().processed() == 1);
    }

    @Test
    void a_delivery_that_does_not_fit_at_all_changes_nothing_and_announces_nothing() throws Exception {
        worker.close();
        Warehouse full = new Warehouse(new WarehouseLayout(1, 1, 1, 1_000), catalog, PlacementStrategy.FIRST_FREE);
        full.store("LOT-X", "P-004", 10); // ocupa la única ubicación
        worker = newWorker(full);
        worker.start();

        buffer.put(new ReceptionMessage("TRK-1", "P-001", 100));

        Await.until("message processed", () -> worker.snapshot().processed() == 1);
        assertThat(availableOf("P-001")).isZero();
        assertThat(notified).isEmpty();
        assertThat(full.snapshot().rejectedUnits()).isEqualTo(100);
    }

    @Test
    void shows_the_message_it_is_processing_and_applies_it_only_when_it_finishes() throws Exception {
        clock.pause();
        buffer.put(new ReceptionMessage("TRK-1", "P-001", 50));

        Await.until("message taken", () -> worker.snapshot().processing() != null);

        assertThat(worker.snapshot().processing().truckId()).isEqualTo("TRK-1");
        assertThat(availableOf("P-001")).isZero();

        clock.resume();
        Await.until("message applied", () -> availableOf("P-001") == 50);
        Await.until("worker idle again", () -> worker.snapshot().processing() == null);
    }

    @Test
    void a_slow_inventory_makes_reception_wait_but_loses_no_message() throws Exception {
        clock.pause(); // Inventario queda procesando el primer mensaje: no retira ninguno más
        int messages = BUFFER_CAPACITY + 3;
        executor.submit(() -> {
            for (int i = 0; i < messages; i++) {
                buffer.put(new ReceptionMessage("TRK-" + i, "P-001", 10));
            }
            return null;
        });

        // 1 en proceso + 2 en el buffer + el resto del productor esperando espacio.
        Await.until("buffer full and producer blocked", () -> buffer.snapshot().size() == BUFFER_CAPACITY
                && buffer.snapshot().blockedProducers() == 1);
        assertThat(buffer.snapshot().accepted()).isEqualTo(BUFFER_CAPACITY + 1);

        clock.resume();

        Await.until("every message processed", () -> availableOf("P-001") == messages * 10);
        assertThat(buffer.snapshot().blockedProducers()).isZero();
        assertThat(worker.snapshot().processed()).isEqualTo(messages);
    }

    @Test
    void discards_an_invalid_message_and_keeps_processing_the_next_ones() throws Exception {
        buffer.put(new ReceptionMessage("TRK-1", "P-999", 10)); // producto que no existe
        buffer.put(new ReceptionMessage("TRK-2", "P-001", 7));

        Await.until("the valid message was applied", () -> availableOf("P-001") == 7);

        assertThat(notified).containsExactly("P-001"); // solo se avisó del mensaje válido
    }

    @Test
    void its_thread_ends_when_closed() throws Exception {
        Await.until("worker running", InventoryWorkerTest::workerThreadAlive);

        worker.close();

        Await.until("worker stopped", () -> !workerThreadAlive());
    }

    private static boolean workerThreadAlive() {
        return Thread.getAllStackTraces().keySet().stream()
                .anyMatch(thread -> thread.getName().equals("inventory-worker") && thread.isAlive());
    }
}
