package com.redxela.scheduling;

import static com.redxela.domain.ServiceLevel.ECONOMY;
import static com.redxela.domain.ServiceLevel.EXPRESS;
import static com.redxela.domain.ServiceLevel.PRIORITY;
import static com.redxela.domain.ServiceLevel.STANDARD;
import static com.redxela.testsupport.TestOrders.STANDARD_PRODUCT;
import static org.assertj.core.api.Assertions.assertThat;

import com.redxela.domain.QueuedOrder;
import com.redxela.domain.ServiceLevel;
import com.redxela.testsupport.TestOrders;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Cada política se prueba con un escenario que la distingue de las demás. */
class SchedulingPolicyTest {

    private static QueuedOrder queued(String id, ServiceLevel level, int units, long arrivalMinutes, long sequence) {
        return TestOrders.queued(id, level, STANDARD_PRODUCT, units, Duration.ofMinutes(arrivalMinutes), sequence);
    }

    private static List<String> order(SchedulingPolicy policy, QueuedOrder... queue) {
        List<QueuedOrder> sorted = new ArrayList<>(List.of(queue));
        sorted.sort(policy.comparator());
        return sorted.stream().map(entry -> entry.order().id()).toList();
    }

    @Test
    void fifo_ignores_service_level_and_size() {
        QueuedOrder economy = queued("A", ECONOMY, 2, 0, 1);
        QueuedOrder express = queued("B", EXPRESS, 30, 5, 2);
        QueuedOrder standard = queued("C", STANDARD, 10, 2, 3);

        assertThat(order(SchedulingPolicy.FIFO, standard, express, economy)).containsExactly("A", "B", "C");
    }

    @Test
    void strict_priority_always_serves_the_most_urgent_level_first() {
        QueuedOrder economy = queued("A", ECONOMY, 2, 0, 1);
        QueuedOrder express = queued("B", EXPRESS, 30, 5, 2);
        QueuedOrder standard = queued("C", STANDARD, 10, 2, 3);

        assertThat(order(SchedulingPolicy.STRICT_PRIORITY, economy, standard, express)).containsExactly("B", "C", "A");
    }

    @Test
    void strict_priority_serves_arrival_order_within_the_same_level() {
        QueuedOrder first = queued("first", STANDARD, 50, 0, 1);
        QueuedOrder second = queued("second", STANDARD, 1, 1, 2);

        assertThat(order(SchedulingPolicy.STRICT_PRIORITY, second, first)).containsExactly("first", "second");
    }

    @Test
    void strict_priority_lets_a_fresh_express_order_overtake_one_that_has_waited_for_hours() {
        QueuedOrder starving = queued("starving", ECONOMY, 5, 0, 1);
        QueuedOrder fresh = queued("fresh", EXPRESS, 5, 600, 2);

        assertThat(order(SchedulingPolicy.STRICT_PRIORITY, starving, fresh)).containsExactly("fresh", "starving");
    }

    @Test
    void aging_lets_a_long_waiting_low_level_order_overtake_a_fresh_urgent_one() {
        // Clave de envejecimiento = llegada + nivel × 10 min: economy 0+50 = 50; express 600+10 = 610.
        QueuedOrder starving = queued("starving", ECONOMY, 5, 0, 1);
        QueuedOrder fresh = queued("fresh", EXPRESS, 5, 600, 2);

        assertThat(order(SchedulingPolicy.AGING_PRIORITY, fresh, starving)).containsExactly("starving", "fresh");
    }

    @Test
    void aging_still_favors_the_urgent_level_when_both_arrive_together() {
        QueuedOrder economy = queued("economy", ECONOMY, 5, 0, 1);
        QueuedOrder express = queued("express", EXPRESS, 5, 0, 2);

        assertThat(order(SchedulingPolicy.AGING_PRIORITY, economy, express)).containsExactly("express", "economy");
    }

    @Test
    void edf_serves_the_order_whose_deadline_comes_first() {
        // Vencimientos: A economy 0+120 = 120; B express 5+1 = 6; C standard 2+30 = 32.
        QueuedOrder economy = queued("A", ECONOMY, 2, 0, 1);
        QueuedOrder express = queued("B", EXPRESS, 30, 5, 2);
        QueuedOrder standard = queued("C", STANDARD, 10, 2, 3);

        assertThat(order(SchedulingPolicy.EARLIEST_DEADLINE_FIRST, economy, standard, express))
                .containsExactly("B", "C", "A");
    }

    @Test
    void edf_differs_from_strict_priority_when_an_older_order_expires_first() {
        // standard llegó en 0 y vence en 30; priority llegó en 25 y vence en 35.
        QueuedOrder older = queued("older-standard", STANDARD, 5, 0, 1);
        QueuedOrder newer = queued("newer-priority", PRIORITY, 5, 25, 2);

        assertThat(order(SchedulingPolicy.EARLIEST_DEADLINE_FIRST, newer, older))
                .containsExactly("older-standard", "newer-priority");
        assertThat(order(SchedulingPolicy.STRICT_PRIORITY, newer, older))
                .containsExactly("newer-priority", "older-standard");
    }

    @Test
    void edf_differs_from_aging_because_it_uses_each_levels_real_target() {
        // economy: vence en 0+120 = 120, envejecimiento 0+50 = 50. express: vence en 60+1 = 61, envejecimiento 60+10 = 70.
        QueuedOrder economy = queued("economy", ECONOMY, 5, 0, 1);
        QueuedOrder express = queued("express", EXPRESS, 5, 60, 2);

        assertThat(order(SchedulingPolicy.EARLIEST_DEADLINE_FIRST, economy, express)).containsExactly("express", "economy");
        assertThat(order(SchedulingPolicy.AGING_PRIORITY, economy, express)).containsExactly("economy", "express");
    }

    @Test
    void shortest_job_first_serves_the_order_with_fewer_items() {
        QueuedOrder economy = queued("A", ECONOMY, 2, 0, 1);
        QueuedOrder express = queued("B", EXPRESS, 30, 5, 2);
        QueuedOrder standard = queued("C", STANDARD, 10, 2, 3);

        assertThat(order(SchedulingPolicy.SHORTEST_JOB_FIRST, express, standard, economy)).containsExactly("A", "C", "B");
    }

    @Test
    void every_policy_breaks_ties_by_arrival_sequence() {
        for (SchedulingPolicy policy : SchedulingPolicy.values()) {
            QueuedOrder first = queued("first", STANDARD, 5, 0, 1);
            QueuedOrder second = queued("second", STANDARD, 5, 0, 2);

            assertThat(order(policy, second, first)).as("policy %s", policy).containsExactly("first", "second");
        }
    }

    @Test
    void every_policy_defines_a_consistent_total_order() {
        // Un comparador inconsistente haría fallar a los ordenamientos de Java con casos de borde.
        Random random = new Random(42);
        ServiceLevel[] levels = ServiceLevel.values();
        List<QueuedOrder> queue = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            queue.add(queued("O-" + i, levels[random.nextInt(levels.length)], 1 + random.nextInt(40),
                    random.nextInt(500), i));
        }

        for (SchedulingPolicy policy : SchedulingPolicy.values()) {
            Collections.shuffle(queue, random);
            List<QueuedOrder> sorted = new ArrayList<>(queue);
            sorted.sort(policy.comparator());
            for (int i = 1; i < sorted.size(); i++) {
                assertThat(policy.comparator().compare(sorted.get(i - 1), sorted.get(i)))
                        .as("policy %s at position %d", policy, i)
                        .isLessThanOrEqualTo(0);
            }
        }
    }

    @Test
    void every_policy_explains_itself_to_the_user() {
        for (SchedulingPolicy policy : SchedulingPolicy.values()) {
            assertThat(policy.displayName()).isNotBlank();
            assertThat(policy.description()).isNotBlank();
        }
        assertThat(SchedulingPolicy.AGING_PRIORITY.description()).contains("10 minutos");
    }
}
