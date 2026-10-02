package com.redxela.processing;

import static com.redxela.domain.ServiceLevel.ECONOMY;
import static com.redxela.domain.ServiceLevel.EXPRESS;
import static com.redxela.testsupport.TestOrders.STANDARD_PRODUCT;
import static org.assertj.core.api.Assertions.assertThat;

import com.redxela.domain.QueuedOrder;
import com.redxela.resources.Operation;
import com.redxela.scheduling.SchedulingPolicy;
import com.redxela.testsupport.FakeOperation;
import com.redxela.testsupport.TestOrders;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OperationOrderingTest {

    private static OrderExecution execution(String id, com.redxela.domain.ServiceLevel level, long sequence) {
        QueuedOrder queued = TestOrders.queued(id, level, STANDARD_PRODUCT, 3, Duration.ZERO, sequence);
        return new OrderExecution(queued, ExecutionPlan.forOrder(queued.order(), ProcessingTimes.defaults()));
    }

    @Test
    void orders_operations_with_the_criterion_of_the_policy() {
        var ordering = OperationOrdering.of(SchedulingPolicy.STRICT_PRIORITY);
        OrderExecution economy = execution("economy", ECONOMY, 1);
        OrderExecution express = execution("express", EXPRESS, 2);

        assertThat(ordering.compare(express, economy)).isNegative();
        assertThat(ordering.compare(economy, express)).isPositive();
    }

    @Test
    void follows_the_policy_when_it_changes() {
        OrderExecution economy = execution("economy", ECONOMY, 1);
        OrderExecution express = execution("express", EXPRESS, 2);

        assertThat(OperationOrdering.of(SchedulingPolicy.FIFO).compare(economy, express)).isNegative();
        assertThat(OperationOrdering.of(SchedulingPolicy.STRICT_PRIORITY).compare(economy, express)).isPositive();
    }

    @Test
    void places_orders_before_other_operations_and_ties_the_others() {
        var ordering = OperationOrdering.of(SchedulingPolicy.STRICT_PRIORITY);
        OrderExecution order = execution("order", ECONOMY, 1);
        FakeOperation other = new FakeOperation("other");
        FakeOperation another = new FakeOperation("another");

        assertThat(ordering.compare(order, other)).isNegative();
        assertThat(ordering.compare(other, order)).isPositive();
        assertThat(ordering.compare(other, another)).isZero();
    }

    @Test
    void is_a_consistent_order_when_sorting_a_mix_of_operations() {
        var ordering = OperationOrdering.of(SchedulingPolicy.STRICT_PRIORITY);
        List<Operation> mixed = new ArrayList<>(List.of(
                new FakeOperation("x"), execution("economy", ECONOMY, 1), new FakeOperation("y"),
                execution("express", EXPRESS, 2)));

        mixed.sort(ordering);

        assertThat(mixed).extracting(Operation::id).containsExactly("express", "economy", "x", "y");
    }
}
