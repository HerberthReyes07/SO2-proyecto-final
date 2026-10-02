package com.redxela.processing;

import static com.redxela.domain.ServiceLevel.ECONOMY;
import static com.redxela.domain.ServiceLevel.EXPRESS;
import static com.redxela.testsupport.TestOrders.STANDARD_PRODUCT;
import static org.assertj.core.api.Assertions.assertThat;

import com.redxela.domain.ServiceLevel;
import com.redxela.resources.ResourceRegistry;
import com.redxela.scheduling.SchedulingPolicy;
import com.redxela.sim.SimClock;
import com.redxela.testsupport.TestOrders;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Prueba la lista de espera del planificador sin arrancar el despachador. */
class OrderSchedulerTest {

    private final SimClock clock = new SimClock();
    private final ResourceRegistry resources = new ResourceRegistry();
    private OrderProcessor processor;
    private OrderScheduler scheduler;

    @BeforeEach
    void setUp() {
        clock.pause(); // el tiempo simulado queda fijo y los cálculos de espera son exactos
        OrderWorkflow workflow = new OrderWorkflow(resources, clock);
        processor = new OrderProcessor(workflow, ProcessingTimes.defaults());
        scheduler = new OrderScheduler(clock, resources, workflow, processor);
    }

    @AfterEach
    void tearDown() {
        scheduler.close();
        processor.close();
    }

    private void enqueue(String id, ServiceLevel level, int units) {
        scheduler.enqueue(TestOrders.order(id, level, STANDARD_PRODUCT, units));
    }

    @Test
    void starts_with_strict_priority() {
        assertThat(scheduler.policy()).isEqualTo(SchedulingPolicy.STRICT_PRIORITY);
        assertThat(scheduler.snapshot(clock.now()).policy()).isEqualTo(SchedulingPolicy.STRICT_PRIORITY);
    }

    @Test
    void lists_waiting_orders_in_the_order_the_policy_will_serve_them() {
        enqueue("economy", ECONOMY, 5);
        enqueue("express", EXPRESS, 5);

        SchedulingSnapshot snapshot = scheduler.snapshot(clock.now());

        assertThat(snapshot.waitingTotal()).isEqualTo(2);
        assertThat(snapshot.waiting()).extracting(WaitingOrderSnapshot::orderId).containsExactly("express", "economy");
    }

    @Test
    void reports_how_long_each_order_has_waited_and_its_target() {
        enqueue("express", EXPRESS, 5);
        Duration arrival = clock.now();

        WaitingOrderSnapshot waiting = scheduler.snapshot(arrival.plusMinutes(5)).waiting().get(0);

        assertThat(waiting.waitedMillis()).isEqualTo(Duration.ofMinutes(5).toMillis());
        assertThat(waiting.targetWaitMillis()).isEqualTo(Duration.ofMinutes(1).toMillis());
        assertThat(waiting.customerName()).isNotBlank();
        assertThat(waiting.totalItems()).isEqualTo(5);
    }

    @Test
    void changing_the_policy_reorders_the_snapshot() {
        enqueue("big-express", EXPRESS, 30);
        enqueue("small-economy", ECONOMY, 1);

        scheduler.setPolicy(SchedulingPolicy.SHORTEST_JOB_FIRST);

        SchedulingSnapshot snapshot = scheduler.snapshot(clock.now());
        assertThat(snapshot.policy()).isEqualTo(SchedulingPolicy.SHORTEST_JOB_FIRST);
        assertThat(snapshot.waiting()).extracting(WaitingOrderSnapshot::orderId)
                .containsExactly("small-economy", "big-express");
    }

    @Test
    void caps_the_snapshot_but_still_reports_the_real_total() {
        int total = OrderScheduler.MAX_WAITING_IN_SNAPSHOT + 25;
        for (int i = 0; i < total; i++) {
            enqueue("O-" + i, EXPRESS, 1);
        }

        SchedulingSnapshot snapshot = scheduler.snapshot(clock.now());

        assertThat(snapshot.waitingTotal()).isEqualTo(total);
        assertThat(snapshot.waiting()).hasSize(OrderScheduler.MAX_WAITING_IN_SNAPSHOT);
    }

    @Test
    void changing_the_policy_also_changes_how_resource_queues_choose() throws Exception {
        scheduler.setPolicy(SchedulingPolicy.FIFO);
        var ordering = OperationOrdering.of(scheduler.policy());
        OrderExecution economy = new OrderExecution(TestOrders.queued(TestOrders.order("E", ECONOMY, STANDARD_PRODUCT, 1), 1),
                ExecutionPlan.forOrder(TestOrders.order("E", ECONOMY, STANDARD_PRODUCT, 1), ProcessingTimes.defaults()));
        OrderExecution express = new OrderExecution(TestOrders.queued(TestOrders.order("X", EXPRESS, STANDARD_PRODUCT, 1), 2),
                ExecutionPlan.forOrder(TestOrders.order("X", EXPRESS, STANDARD_PRODUCT, 1), ProcessingTimes.defaults()));
        var packing = resources.pool(com.redxela.domain.ResourceType.SCANNER);
        packing.acquire(new com.redxela.testsupport.FakeOperation("holder"));
        Thread first = Thread.ofPlatform().start(() -> acquireQuietly(packing, economy));
        awaitWaiting(packing, 1);
        Thread second = Thread.ofPlatform().start(() -> acquireQuietly(packing, express));
        awaitWaiting(packing, 2);
        assertThat(packing.snapshot().waiting()).containsExactly("E", "X"); // FIFO: llegó antes

        scheduler.setPolicy(SchedulingPolicy.STRICT_PRIORITY);

        assertThat(packing.snapshot().waiting()).containsExactly("X", "E"); // ahora gana el Exprés
        first.interrupt();
        second.interrupt();
        assertThat(ordering.compare(economy, express)).isNegative(); // el comparador viejo seguía siendo FIFO
    }

    private static void acquireQuietly(com.redxela.resources.ResourcePool pool, OrderExecution execution) {
        try {
            pool.acquire(execution);
        } catch (InterruptedException | com.redxela.resources.OperationPreemptedException e) {
            // El test interrumpe estos hilos al terminar; no hay nada más que hacer.
        }
    }

    private static void awaitWaiting(com.redxela.resources.ResourcePool pool, int count) throws InterruptedException {
        com.redxela.testsupport.Await.until("waiters in " + pool.type(), () -> pool.snapshot().waiting().size() == count);
    }
}
