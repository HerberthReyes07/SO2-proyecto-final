package com.redxela.scheduling;

import static com.redxela.domain.ServiceLevel.ECONOMY;
import static com.redxela.domain.ServiceLevel.EXPRESS;
import static com.redxela.testsupport.TestOrders.STANDARD_PRODUCT;
import static org.assertj.core.api.Assertions.assertThat;

import com.redxela.domain.QueuedOrder;
import com.redxela.domain.ServiceLevel;
import com.redxela.testsupport.Await;
import com.redxela.testsupport.TestOrders;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 20, unit = TimeUnit.SECONDS)
class WaitingListTest {

    private static final Predicate<QueuedOrder> ANY = queued -> true;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @AfterEach
    void shutdownThreads() {
        executor.shutdownNow();
    }

    private static QueuedOrder queued(String id, ServiceLevel level, int units, long sequence) {
        return TestOrders.queued(id, level, STANDARD_PRODUCT, units, Duration.ofMinutes(sequence), sequence);
    }

    @Test
    void takes_the_best_order_according_to_the_current_policy_and_removes_it() {
        WaitingList list = new WaitingList(SchedulingPolicy.STRICT_PRIORITY);
        list.add(queued("economy", ECONOMY, 1, 1));
        list.add(queued("express", EXPRESS, 1, 2));

        assertThat(list.takeBest(ANY)).map(entry -> entry.order().id()).contains("express");
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.takeBest(ANY)).map(entry -> entry.order().id()).contains("economy");
        assertThat(list.takeBest(ANY)).isEmpty();
    }

    @Test
    void snapshot_lists_orders_in_service_order_without_removing_them() {
        WaitingList list = new WaitingList(SchedulingPolicy.STRICT_PRIORITY);
        list.add(queued("economy", ECONOMY, 1, 1));
        list.add(queued("express", EXPRESS, 1, 2));

        assertThat(list.snapshot()).extracting(entry -> entry.order().id()).containsExactly("express", "economy");
        assertThat(list.size()).isEqualTo(2);
    }

    @Test
    void changing_the_policy_reorders_the_orders_already_waiting() {
        WaitingList list = new WaitingList(SchedulingPolicy.STRICT_PRIORITY);
        list.add(queued("big-express", EXPRESS, 30, 1));
        list.add(queued("small-economy", ECONOMY, 1, 2));
        assertThat(list.snapshot()).extracting(entry -> entry.order().id()).containsExactly("big-express", "small-economy");

        list.setPolicy(SchedulingPolicy.SHORTEST_JOB_FIRST);

        assertThat(list.policy()).isEqualTo(SchedulingPolicy.SHORTEST_JOB_FIRST);
        assertThat(list.snapshot()).extracting(entry -> entry.order().id()).containsExactly("small-economy", "big-express");
        assertThat(list.takeBest(ANY)).map(entry -> entry.order().id()).contains("small-economy");
    }

    @Test
    void awaiting_blocks_until_an_order_arrives() throws Exception {
        WaitingList list = new WaitingList(SchedulingPolicy.FIFO);
        AtomicBoolean released = new AtomicBoolean();
        Future<?> waiter = executor.submit(() -> {
            list.awaitEligible(ANY);
            released.set(true);
            return null;
        });

        assertThat(awaitsBriefly(waiter)).isFalse(); // sigue bloqueado con la lista vacía
        assertThat(released).isFalse();

        list.add(queued("a", EXPRESS, 1, 1));
        waiter.get(5, TimeUnit.SECONDS);

        assertThat(released).isTrue();
        assertThat(list.size()).isEqualTo(1); // esperar no retira el pedido
    }

    @Test
    void awaiting_can_be_interrupted() {
        WaitingList list = new WaitingList(SchedulingPolicy.FIFO);
        Future<?> waiter = executor.submit(() -> {
            list.awaitEligible(ANY);
            return null;
        });

        waiter.cancel(true);

        assertThat(waiter.isCancelled()).isTrue();
    }

    @Test
    void concurrent_producers_and_one_consumer_never_lose_or_duplicate_an_order() throws Exception {
        WaitingList list = new WaitingList(SchedulingPolicy.STRICT_PRIORITY);
        int producers = 8;
        int perProducer = 150;
        List<String> taken = Collections.synchronizedList(new ArrayList<>());

        Future<?> consumer = executor.submit(() -> {
            while (taken.size() < producers * perProducer) {
                list.awaitEligible(ANY);
                list.takeBest(ANY).ifPresent(entry -> taken.add(entry.order().id()));
            }
            return null;
        });
        List<Future<?>> producing = new ArrayList<>();
        for (int p = 0; p < producers; p++) {
            int producer = p;
            producing.add(executor.submit(() -> {
                for (int i = 0; i < perProducer; i++) {
                    long sequence = (long) producer * perProducer + i;
                    list.add(queued("O-" + sequence, ServiceLevel.values()[i % 5], 1 + i % 9, sequence));
                }
                return null;
            }));
        }
        for (Future<?> task : producing) {
            task.get(10, TimeUnit.SECONDS);
        }
        consumer.get(10, TimeUnit.SECONDS);

        assertThat(taken).hasSize(producers * perProducer).doesNotHaveDuplicates();
        assertThat(list.size()).isZero();
    }

    /** Devuelve true si la tarea terminó en un instante breve; sirve para comprobar que sigue bloqueada. */
    private static boolean awaitsBriefly(Future<?> task) throws InterruptedException {
        try {
            task.get(150, TimeUnit.MILLISECONDS);
            return true;
        } catch (java.util.concurrent.TimeoutException e) {
            return false;
        } catch (java.util.concurrent.ExecutionException e) {
            throw new AssertionError("The waiting task failed", e);
        }
    }
}
