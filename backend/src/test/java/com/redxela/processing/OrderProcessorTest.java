package com.redxela.processing;

import static com.redxela.testsupport.TestOrders.HEAVY_PRODUCT;
import static com.redxela.testsupport.TestOrders.STANDARD_PRODUCT;
import static org.assertj.core.api.Assertions.assertThat;

import com.redxela.domain.CompletedOrder;
import com.redxela.domain.Order;
import com.redxela.domain.OrderStatus;
import com.redxela.domain.ResourceType;
import com.redxela.resources.ResourceRegistry;
import com.redxela.sim.SimClock;
import com.redxela.testsupport.Await;
import com.redxela.testsupport.TestOrders;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Prueba al procesador por sí solo: el test hace de despachador asignando el encargado a mano. */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class OrderProcessorTest {

    private final ResourceRegistry registry = new ResourceRegistry();
    private final SimClock clock = new SimClock();
    private final OrderWorkflow workflow = new OrderWorkflow(registry, clock);
    private final OrderProcessor processor = new OrderProcessor(workflow, ProcessingTimes.defaults());
    private long arrivals;

    @AfterEach
    void closeProcessor() {
        processor.close();
    }

    @Test
    void runs_many_admitted_orders_concurrently_and_leaves_no_resource_held() throws Exception {
        clock.setSpeed(SimClock.MAX_SPEED);
        int orders = 40; // se admiten por tandas de 10 (los encargados), como lo haría el despachador
        for (int i = 0; i < orders; i++) {
            workflow.awaitFreeWorker();
            String product = i % 3 == 0 ? HEAVY_PRODUCT : STANDARD_PRODUCT;
            admit(TestOrders.order("O-" + i, product, 1 + i % 7));
        }

        Await.until("all orders completed", () -> processor.completedCount() == orders);

        assertThat(processor.activeOrders(clock.now())).isEmpty();
        registry.snapshots().forEach(pool -> {
            assertThat(pool.holders()).as("holders of %s", pool.type()).isEmpty();
            assertThat(pool.waiting()).as("waiting on %s", pool.type()).isEmpty();
        });
    }

    @Test
    void notifies_the_listeners_of_every_completed_order() throws Exception {
        clock.setSpeed(SimClock.MAX_SPEED);
        List<CompletedOrder> summaries = new CopyOnWriteArrayList<>();
        processor.addCompletionListener(summaries::add);

        for (int i = 0; i < 3; i++) {
            workflow.awaitFreeWorker();
            admit(TestOrders.order("O-" + i, STANDARD_PRODUCT, 2));
        }

        Await.until("three summaries", () -> summaries.size() == 3);
        assertThat(summaries).extracting(CompletedOrder::orderId).containsExactlyInAnyOrder("O-0", "O-1", "O-2");
    }

    @Test
    void a_failing_listener_neither_breaks_the_order_nor_hides_it_from_the_other_listeners() throws Exception {
        clock.setSpeed(SimClock.MAX_SPEED);
        List<CompletedOrder> summaries = new CopyOnWriteArrayList<>();
        processor.addCompletionListener(summary -> {
            throw new IllegalStateException("broken listener");
        });
        processor.addCompletionListener(summaries::add);

        admit(TestOrders.order("O-1", STANDARD_PRODUCT, 2));

        Await.until("order completed", () -> processor.completedCount() == 1);
        assertThat(summaries).hasSize(1);
    }

    @Test
    void does_not_notify_orders_that_were_interrupted_before_finishing() throws Exception {
        clock.pause();
        List<CompletedOrder> summaries = new CopyOnWriteArrayList<>();
        processor.addCompletionListener(summaries::add);
        admit(TestOrders.order("O-1", HEAVY_PRODUCT, 2));
        Await.until("working", () -> registry.pool(ResourceType.FORKLIFT).snapshot().holders().contains("O-1"));

        processor.close();
        Await.until("order removed", () -> processor.activeOrders(Duration.ZERO).isEmpty());

        assertThat(summaries).isEmpty();
    }

    @Test
    void active_orders_are_listed_in_arrival_order_with_their_status() throws Exception {
        clock.pause(); // congela los pedidos para poder observarlos
        admit(TestOrders.order("O-1", STANDARD_PRODUCT, 2));
        admit(TestOrders.order("O-2", HEAVY_PRODUCT, 2));
        admit(TestOrders.order("O-3", STANDARD_PRODUCT, 2));

        Await.until("all three working", () -> processor.activeOrders(clock.now()).stream()
                .filter(order -> order.status() == OrderStatus.PROCESSING && order.awaiting() == null)
                .count() == 3);

        assertThat(processor.activeOrders(clock.now())).extracting(OrderProgressSnapshot::orderId)
                .containsExactly("O-1", "O-2", "O-3");
        assertThat(processor.completedCount()).isZero();
    }

    @Test
    void closing_interrupts_running_orders_and_releases_their_resources() throws Exception {
        clock.pause();
        admit(TestOrders.order("O-1", HEAVY_PRODUCT, 2));
        Await.until("working", () -> registry.pool(ResourceType.FORKLIFT).snapshot().holders().contains("O-1"));

        processor.close();

        Await.until("order removed and resources released", () -> processor.activeOrders(Duration.ZERO).isEmpty()
                && registry.snapshots().stream().allMatch(pool -> pool.holders().isEmpty()));
        assertThat(processor.completedCount()).isZero();
    }

    private void admit(Order order) throws Exception {
        OrderExecution execution = processor.newExecution(TestOrders.queued(order, ++arrivals));
        workflow.assignWorker(execution);
        processor.start(execution);
    }
}
