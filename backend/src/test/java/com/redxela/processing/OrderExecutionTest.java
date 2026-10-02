package com.redxela.processing;

import static org.assertj.core.api.Assertions.assertThat;

import com.redxela.domain.OrderStatus;
import com.redxela.domain.ResourceType;
import com.redxela.domain.Stage;
import com.redxela.testsupport.TestOrders;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Verifica el cálculo de avance y tiempo restante a partir de las transiciones de estado. */
class OrderExecutionTest {

    // Pedido estándar de 10 unidades: picking 80 s + empaque 200 s + despacho 50 s = 330 s de trabajo.
    private final OrderExecution execution = new OrderExecution(
            TestOrders.order("O-1", TestOrders.STANDARD_PRODUCT, 10),
            ExecutionPlan.forOrder(TestOrders.order("O-1", TestOrders.STANDARD_PRODUCT, 10), ProcessingTimes.defaults()),
            1);

    @Test
    void a_new_execution_has_no_progress_and_all_the_work_remaining() {
        OrderProgressSnapshot snapshot = execution.progress(Duration.ZERO);

        assertThat(snapshot.progress()).isZero();
        assertThat(snapshot.remainingMillis()).isEqualTo(330_000);
        assertThat(snapshot.status()).isEqualTo(OrderStatus.WAITING);
        assertThat(snapshot.stage()).isEqualTo(Stage.PICKING);
        assertThat(snapshot.held()).isEmpty();
    }

    @Test
    void progress_grows_with_simulated_time_inside_a_stage() {
        execution.beginWork(Stage.PICKING, Duration.ofSeconds(80), Duration.ofSeconds(100));

        OrderProgressSnapshot snapshot = execution.progress(Duration.ofSeconds(140)); // 40 s dentro de la etapa

        assertThat(snapshot.progress()).isEqualTo(40.0 / 330.0);
        assertThat(snapshot.remainingMillis()).isEqualTo(290_000);
    }

    @Test
    void progress_inside_a_stage_never_exceeds_the_stage_work() {
        execution.beginWork(Stage.PICKING, Duration.ofSeconds(80), Duration.ZERO);

        OrderProgressSnapshot snapshot = execution.progress(Duration.ofHours(5));

        assertThat(snapshot.progress()).isEqualTo(80.0 / 330.0);
    }

    @Test
    void finished_stages_keep_counting_while_waiting_for_the_next_resource() {
        execution.beginWork(Stage.PICKING, Duration.ofSeconds(80), Duration.ZERO);
        execution.finishStageWork();
        execution.beginWaiting(Stage.PACKING, ResourceType.PACKING_STATION);

        OrderProgressSnapshot snapshot = execution.progress(Duration.ofHours(1));

        assertThat(snapshot.progress()).isEqualTo(80.0 / 330.0);
        assertThat(snapshot.awaiting()).isEqualTo(ResourceType.PACKING_STATION);
        assertThat(snapshot.stage()).isEqualTo(Stage.PACKING);
    }

    @Test
    void completing_every_stage_reaches_full_progress() {
        for (StagePlan stage : execution.plan().stages()) {
            execution.beginWork(stage.stage(), stage.work(), Duration.ZERO);
            execution.finishStageWork();
        }

        OrderProgressSnapshot snapshot = execution.progress(Duration.ZERO);

        assertThat(snapshot.progress()).isEqualTo(1.0);
        assertThat(snapshot.remainingMillis()).isZero();
    }

    @Test
    void tracks_held_resources_in_global_order_and_clears_awaiting_on_acquire() {
        execution.beginWaiting(Stage.PICKING, ResourceType.WAREHOUSE_WORKER);
        execution.resourceAcquired(ResourceType.WAREHOUSE_WORKER);
        execution.beginWaiting(Stage.PACKING, ResourceType.PACKING_STATION);
        execution.resourceAcquired(ResourceType.PACKING_STATION);
        execution.resourceAcquired(ResourceType.FORKLIFT);

        OrderProgressSnapshot afterAcquiring = execution.progress(Duration.ZERO);
        execution.resourceReleased(ResourceType.FORKLIFT);
        OrderProgressSnapshot afterReleasing = execution.progress(Duration.ZERO);

        assertThat(afterAcquiring.held()).containsExactly(
                ResourceType.WAREHOUSE_WORKER, ResourceType.FORKLIFT, ResourceType.PACKING_STATION);
        assertThat(afterAcquiring.awaiting()).isNull();
        assertThat(afterReleasing.held()).containsExactly(ResourceType.WAREHOUSE_WORKER, ResourceType.PACKING_STATION);
    }
}
