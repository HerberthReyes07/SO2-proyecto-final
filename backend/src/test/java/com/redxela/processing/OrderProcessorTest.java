package com.redxela.processing;

import static com.redxela.testsupport.TestOrders.HEAVY_PRODUCT;
import static com.redxela.testsupport.TestOrders.STANDARD_PRODUCT;
import static org.assertj.core.api.Assertions.assertThat;

import com.redxela.domain.OrderStatus;
import com.redxela.resources.ResourceRegistry;
import com.redxela.sim.SimClock;
import com.redxela.testsupport.Await;
import com.redxela.testsupport.TestOrders;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class OrderProcessorTest {

    private final ResourceRegistry registry = new ResourceRegistry();
    private final SimClock clock = new SimClock();
    private final OrderProcessor processor =
            new OrderProcessor(new OrderWorkflow(registry, clock), ProcessingTimes.defaults());

    @AfterEach
    void closeProcessor() {
        processor.close();
    }

    @Test
    void processes_many_orders_concurrently_and_leaves_no_resource_held() throws Exception {
        clock.setSpeed(SimClock.MAX_SPEED);
        int orders = 60; // más que los 10 encargados: obliga a que los pedidos compitan y esperen
        for (int i = 0; i < orders; i++) {
            String product = i % 3 == 0 ? HEAVY_PRODUCT : STANDARD_PRODUCT;
            processor.submit(TestOrders.order("O-" + i, product, 1 + i % 7));
        }

        Await.until("all orders completed", () -> processor.completedCount() == orders);

        assertThat(processor.activeOrders(clock.now())).isEmpty();
        registry.snapshots().forEach(pool -> {
            assertThat(pool.holders()).as("holders of %s", pool.type()).isEmpty();
            assertThat(pool.waiting()).as("waiting on %s", pool.type()).isEmpty();
        });
    }

    @Test
    void active_orders_are_listed_in_arrival_order_with_their_status() throws Exception {
        clock.pause(); // congela los pedidos para poder observarlos
        processor.submit(TestOrders.order("O-1", STANDARD_PRODUCT, 2));
        processor.submit(TestOrders.order("O-2", HEAVY_PRODUCT, 2));
        processor.submit(TestOrders.order("O-3", STANDARD_PRODUCT, 2));

        Await.until("all three working", () -> processor.activeOrders(clock.now()).stream()
                .filter(order -> order.status() == OrderStatus.PROCESSING)
                .count() == 3);

        assertThat(processor.activeOrders(clock.now())).extracting(OrderProgressSnapshot::orderId)
                .containsExactly("O-1", "O-2", "O-3");
        assertThat(processor.completedCount()).isZero();
    }

    @Test
    void closing_interrupts_running_orders_and_releases_their_resources() throws Exception {
        clock.pause();
        processor.submit(TestOrders.order("O-1", HEAVY_PRODUCT, 2));
        Await.until("working", () -> registry.pool(com.redxela.domain.ResourceType.FORKLIFT)
                .snapshot().holders().contains("O-1"));

        processor.close();

        Await.until("order removed and resources released", () -> processor.activeOrders(Duration.ZERO).isEmpty()
                && registry.snapshots().stream().allMatch(pool -> pool.holders().isEmpty()));
        assertThat(processor.completedCount()).isZero();
    }
}
