package com.redxela.processing;

import static com.redxela.testsupport.TestOrders.BULKY_PRODUCT;
import static com.redxela.testsupport.TestOrders.FRAGILE_PRODUCT;
import static com.redxela.testsupport.TestOrders.HEAVY_PRODUCT;
import static com.redxela.testsupport.TestOrders.PERISHABLE_PRODUCT;
import static com.redxela.testsupport.TestOrders.STANDARD_PRODUCT;
import static org.assertj.core.api.Assertions.assertThat;

import com.redxela.domain.Customer;
import com.redxela.domain.Order;
import com.redxela.domain.OrderLine;
import com.redxela.domain.ResourceType;
import com.redxela.domain.ServiceLevel;
import com.redxela.domain.Stage;
import com.redxela.testsupport.TestOrders;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExecutionPlanTest {

    private final ProcessingTimes times = ProcessingTimes.defaults();

    @Test
    void standard_order_skips_the_inspection_stage() {
        ExecutionPlan plan = ExecutionPlan.forOrder(TestOrders.order("O-1", STANDARD_PRODUCT, 10), times);

        assertThat(plan.stages()).extracting(StagePlan::stage)
                .containsExactly(Stage.PICKING, Stage.PACKING, Stage.DISPATCH);
    }

    @Test
    void heavy_order_uses_the_forklift_during_picking() {
        ExecutionPlan plan = ExecutionPlan.forOrder(TestOrders.order("O-1", HEAVY_PRODUCT, 2), times);

        assertThat(plan.stages().get(0).resources()).containsExactly(ResourceType.FORKLIFT);
        assertThat(plan.stages().get(1).resources()).containsExactly(ResourceType.PACKING_STATION);
        assertThat(plan.stages().get(2).resources()).containsExactly(ResourceType.SCANNER, ResourceType.LOADING_DOCK);
    }

    @Test
    void perishable_order_has_no_packing_stage() {
        ExecutionPlan plan = ExecutionPlan.forOrder(TestOrders.order("O-1", PERISHABLE_PRODUCT, 3), times);

        assertThat(plan.stages()).extracting(StagePlan::stage)
                .containsExactly(Stage.PICKING, Stage.INSPECTION, Stage.DISPATCH);
    }

    @Test
    void bulky_order_goes_through_every_stage() {
        ExecutionPlan plan = ExecutionPlan.forOrder(TestOrders.order("O-1", BULKY_PRODUCT, 1), times);

        assertThat(plan.stages()).extracting(StagePlan::stage).containsExactly(Stage.values());
    }

    @Test
    void picking_always_exists_even_when_it_needs_no_extra_resource() {
        ExecutionPlan plan = ExecutionPlan.forOrder(TestOrders.order("O-1", FRAGILE_PRODUCT, 1), times);

        assertThat(plan.stages().get(0).stage()).isEqualTo(Stage.PICKING);
        assertThat(plan.stages().get(0).resources()).isEmpty();
    }

    @Test
    void work_scales_with_the_number_of_units() {
        ExecutionPlan small = ExecutionPlan.forOrder(TestOrders.order("O-1", STANDARD_PRODUCT, 1), times);
        ExecutionPlan large = ExecutionPlan.forOrder(TestOrders.order("O-2", STANDARD_PRODUCT, 10), times);

        assertThat(large.totalWork()).isEqualTo(small.totalWork().multipliedBy(10));
        // 10 unidades: picking 80 s + empaque 200 s + despacho 50 s
        assertThat(large.totalWork()).isEqualTo(Duration.ofSeconds(330));
    }

    @Test
    void mixed_order_plan_is_the_union_of_its_lines() {
        Customer customer = new Customer("C", "Cliente", ServiceLevel.STANDARD);
        Order mixed = new Order("O-1", customer, List.of(
                new OrderLine(TestOrders.product(STANDARD_PRODUCT), 2),
                new OrderLine(TestOrders.product(HEAVY_PRODUCT), 1)));

        ExecutionPlan plan = ExecutionPlan.forOrder(mixed, times);

        assertThat(plan.stages().stream().flatMap(stage -> stage.resources().stream()).toList())
                .containsExactly(ResourceType.FORKLIFT, ResourceType.PACKING_STATION,
                        ResourceType.SCANNER, ResourceType.LOADING_DOCK);
        // El trabajo se calcula sobre el total de artículos del pedido (3), no por línea.
        assertThat(plan.stages().get(0).work()).isEqualTo(Duration.ofSeconds(8 * 3));
    }
}
