package com.redxela.processing;

import static com.redxela.domain.ServiceLevel.ECONOMY;
import static com.redxela.domain.ServiceLevel.EXPRESS;
import static com.redxela.domain.ServiceLevel.STANDARD;
import static com.redxela.testsupport.TestOrders.STANDARD_PRODUCT;
import static org.assertj.core.api.Assertions.assertThat;

import com.redxela.catalog.ProductCatalog;
import com.redxela.domain.ResourceType;
import com.redxela.domain.ServiceLevel;
import com.redxela.inventory.Inventory;
import com.redxela.resources.OperationPreemptedException;
import com.redxela.resources.ResourcePool;
import com.redxela.resources.ResourceRegistry;
import com.redxela.scheduling.SchedulingPolicy;
import com.redxela.sim.SimClock;
import com.redxela.testsupport.Await;
import com.redxela.testsupport.FakeOperation;
import com.redxela.testsupport.TestOrders;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Pruebas del despachador con un reloj pausado: los pedidos admitidos quedan congelados reteniendo
 * su encargado, y ocupar los encargados con operaciones falsas permite liberar uno a la vez y ver
 * cuál es el pedido que se admite.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class DispatcherTest {

    private final SimClock clock = new SimClock();
    // Existencias de sobra: estos tests no se ocupan del stock.
    private final Inventory inventory = new Inventory(ProductCatalog.defaultCatalog(), 1_000);
    private final ResourceRegistry resources = new ResourceRegistry();
    private final ResourcePool workers = resources.pool(ResourceType.WAREHOUSE_WORKER);
    private OrderProcessor processor;
    private OrderScheduler scheduler;

    @BeforeEach
    void setUp() {
        clock.pause();
        OrderWorkflow workflow = new OrderWorkflow(resources, clock);
        processor = new OrderProcessor(workflow, ProcessingTimes.defaults());
        scheduler = new OrderScheduler(clock, resources, workflow, processor, inventory);
    }

    @AfterEach
    void tearDown() {
        scheduler.close();
        processor.close();
    }

    @Test
    void never_admits_more_orders_than_there_are_workers_and_keeps_the_rest_waiting() throws Exception {
        scheduler.start();
        for (int i = 0; i < 15; i++) {
            enqueue("O-" + i, STANDARD, 3);
        }

        Await.until("ten orders admitted", () -> activeIds().size() == 10);
        Await.until("five orders waiting", () -> scheduler.snapshot(clock.now()).waitingTotal() == 5);

        assertThat(workers.snapshot().holders()).hasSize(10);
        // El despachador espera capacidad sin ocupar ni hacer fila en el pool de encargados.
        assertThat(workers.snapshot().waiting()).isEmpty();
    }

    @Test
    void admits_the_order_the_policy_prefers_each_time_a_worker_frees_up() throws Exception {
        List<FakeOperation> busyWorkers = occupyAllWorkers();
        enqueue("economy", ECONOMY, 5);
        enqueue("express", EXPRESS, 5);
        enqueue("standard", STANDARD, 5);
        scheduler.start();

        List<String> admissionOrder = new ArrayList<>();
        for (int round = 0; round < 3; round++) {
            Set<String> before = activeIds();
            workers.release(busyWorkers.get(round));
            Await.until("an order admitted in round " + round, () -> activeIds().size() == before.size() + 1);
            Set<String> newcomers = activeIds();
            newcomers.removeAll(before);
            admissionOrder.addAll(newcomers);
        }

        assertThat(admissionOrder).containsExactly("express", "standard", "economy");
    }

    @Test
    void decides_late_so_an_urgent_order_arriving_while_no_worker_is_free_goes_first() throws Exception {
        List<FakeOperation> busyWorkers = occupyAllWorkers();
        scheduler.start();
        enqueue("economy-already-waiting", ECONOMY, 5);
        Await.until("dispatcher is holding back", () -> scheduler.snapshot(clock.now()).waitingTotal() == 1);

        enqueue("express-arrives-later", EXPRESS, 5); // llega mientras no hay encargado libre
        workers.release(busyWorkers.get(0));

        Await.until("one order admitted", () -> activeIds().size() == 1);
        assertThat(activeIds()).containsExactly("express-arrives-later");
        assertThat(scheduler.snapshot(clock.now()).waiting()).extracting(WaitingOrderSnapshot::orderId)
                .containsExactly("economy-already-waiting");
    }

    @Test
    void a_policy_change_applies_to_orders_that_were_already_waiting() throws Exception {
        List<FakeOperation> busyWorkers = occupyAllWorkers();
        enqueue("big-express", EXPRESS, 30);
        enqueue("small-economy", ECONOMY, 1);
        scheduler.start();

        scheduler.setPolicy(SchedulingPolicy.SHORTEST_JOB_FIRST); // con la estricta habría ganado el Exprés
        workers.release(busyWorkers.get(0));

        Await.until("one order admitted", () -> activeIds().size() == 1);
        assertThat(activeIds()).containsExactly("small-economy");
    }

    @Test
    void admitted_orders_are_marked_as_processing_and_hold_a_worker() throws Exception {
        scheduler.start();
        enqueue("O-1", STANDARD, 3);

        Await.until("order admitted", () -> activeIds().contains("O-1"));

        assertThat(workers.snapshot().holders()).containsExactly("O-1");
        assertThat(processor.activeOrders(clock.now()).get(0).held()).contains(ResourceType.WAREHOUSE_WORKER);
    }

    @Test
    void the_dispatcher_thread_ends_when_the_scheduler_is_closed() throws Exception {
        scheduler.start();
        Await.until("dispatcher running", () -> dispatcherAlive());

        scheduler.close();

        Await.until("dispatcher stopped", () -> !dispatcherAlive());
    }

    @Test
    void returns_the_order_and_the_worker_and_stops_when_the_processor_is_already_closed() throws Exception {
        processor.close();
        scheduler.start();

        enqueue("O-1", STANDARD, 3);

        Await.until("dispatcher gave up", () -> !dispatcherAlive());
        assertThat(workers.snapshot().holders()).isEmpty(); // devolvió el encargado
        assertThat(scheduler.snapshot(clock.now()).waiting()).extracting(WaitingOrderSnapshot::orderId)
                .containsExactly("O-1"); // y no perdió el pedido
    }

    // ---- Utilidades ----

    private void enqueue(String id, ServiceLevel level, int units) {
        scheduler.enqueue(TestOrders.order(id, level, STANDARD_PRODUCT, units));
    }

    private Set<String> activeIds() {
        return processor.activeOrders(clock.now()).stream()
                .map(OrderProgressSnapshot::orderId)
                .collect(Collectors.toSet());
    }

    private List<FakeOperation> occupyAllWorkers() throws InterruptedException, OperationPreemptedException {
        List<FakeOperation> blockers = new ArrayList<>();
        for (int i = 0; i < ResourceType.WAREHOUSE_WORKER.capacity(); i++) {
            FakeOperation blocker = new FakeOperation("busy-worker-" + i);
            workers.acquire(blocker);
            blockers.add(blocker);
        }
        return blockers;
    }

    private static boolean dispatcherAlive() {
        return Thread.getAllStackTraces().keySet().stream()
                .anyMatch(thread -> thread.getName().equals("dispatcher") && thread.isAlive());
    }
}
