package com.redxela.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.redxela.testsupport.TestOrders;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class QueuedOrderTest {

    private final Order order = TestOrders.order("O-1", TestOrders.STANDARD_PRODUCT, 3);

    @Test
    void a_new_order_has_no_retries() {
        assertThat(new QueuedOrder(order, Duration.ofMinutes(5), 7).retries()).isZero();
    }

    @Test
    void returning_an_order_to_the_list_keeps_its_arrival_and_sequence_and_counts_a_retry() {
        QueuedOrder original = new QueuedOrder(order, Duration.ofMinutes(5), 7);

        QueuedOrder returned = original.withRetry().withRetry();

        assertThat(returned.order()).isSameAs(order);
        assertThat(returned.arrivalTime()).isEqualTo(Duration.ofMinutes(5)); // no pierde su lugar ni su antigüedad
        assertThat(returned.sequence()).isEqualTo(7);
        assertThat(returned.retries()).isEqualTo(2);
        assertThat(original.retries()).as("the original record is immutable").isZero();
    }
}
