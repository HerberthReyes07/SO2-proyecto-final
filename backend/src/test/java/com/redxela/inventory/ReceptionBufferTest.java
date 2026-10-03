package com.redxela.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.redxela.testsupport.Await;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class ReceptionBufferTest {

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @AfterEach
    void shutdownThreads() {
        executor.shutdownNow();
    }

    private static ReceptionMessage message(String truckId) {
        return new ReceptionMessage(truckId, "P-001", 10);
    }

    private static boolean isStillBlocked(Future<?> task) throws Exception {
        try {
            task.get(150, TimeUnit.MILLISECONDS);
            return false;
        } catch (TimeoutException e) {
            return true;
        }
    }

    @Test
    void delivers_messages_in_the_order_they_arrived() throws Exception {
        ReceptionBuffer buffer = new ReceptionBuffer(5);
        buffer.put(message("A"));
        buffer.put(message("B"));
        buffer.put(message("C"));

        assertThat(List.of(buffer.take().truckId(), buffer.take().truckId(), buffer.take().truckId()))
                .containsExactly("A", "B", "C");
    }

    @Test
    void a_producer_waits_when_the_buffer_is_full_and_continues_once_there_is_space() throws Exception {
        ReceptionBuffer buffer = new ReceptionBuffer(2);
        buffer.put(message("A"));
        buffer.put(message("B"));
        Future<?> producer = executor.submit(() -> {
            buffer.put(message("C"));
            return null;
        });

        assertThat(isStillBlocked(producer)).isTrue();
        assertThat(buffer.snapshot().blockedProducers()).isEqualTo(1);
        assertThat(buffer.snapshot().size()).isEqualTo(2); // el mensaje bloqueado no se perdió ni se coló

        assertThat(buffer.take().truckId()).isEqualTo("A");
        producer.get(5, TimeUnit.SECONDS);

        assertThat(buffer.snapshot().blockedProducers()).isZero();
        assertThat(buffer.take().truckId()).isEqualTo("B");
        assertThat(buffer.take().truckId()).isEqualTo("C");
    }

    @Test
    void the_consumer_waits_when_the_buffer_is_empty_and_wakes_up_with_the_next_message() throws Exception {
        ReceptionBuffer buffer = new ReceptionBuffer(2);
        Future<ReceptionMessage> consumer = executor.submit(buffer::take);

        assertThat(isStillBlocked(consumer)).isTrue();
        buffer.put(message("A"));

        assertThat(consumer.get(5, TimeUnit.SECONDS).truckId()).isEqualTo("A");
    }

    @Test
    void counts_what_it_accepted_and_what_it_delivered() throws Exception {
        ReceptionBuffer buffer = new ReceptionBuffer(4);
        buffer.put(message("A"));
        buffer.put(message("B"));
        buffer.take();

        ReceptionBufferSnapshot snapshot = buffer.snapshot();

        assertThat(snapshot).isEqualTo(new ReceptionBufferSnapshot(4, 1, 0, 2, 1));
    }

    @Test
    void many_fast_producers_and_a_slow_consumer_lose_and_duplicate_nothing_and_keep_each_producers_order()
            throws Exception {
        ReceptionBuffer buffer = new ReceptionBuffer(3); // muy chico a propósito: obliga a esperar todo el tiempo
        int producers = 6;
        int perProducer = 100;
        List<String> received = Collections.synchronizedList(new ArrayList<>());

        Future<?> consumer = executor.submit(() -> {
            for (int i = 0; i < producers * perProducer; i++) {
                received.add(buffer.take().truckId());
                if (i % 50 == 0) {
                    Thread.sleep(1); // consumidor más lento que los productores
                }
            }
            return null;
        });
        List<Future<?>> producing = new ArrayList<>();
        for (int p = 0; p < producers; p++) {
            int producer = p;
            producing.add(executor.submit(() -> {
                for (int i = 0; i < perProducer; i++) {
                    buffer.put(message("P" + producer + "-" + i));
                }
                return null;
            }));
        }
        for (Future<?> task : producing) {
            task.get(20, TimeUnit.SECONDS);
        }
        consumer.get(20, TimeUnit.SECONDS);

        assertThat(received).hasSize(producers * perProducer).doesNotHaveDuplicates();
        for (int p = 0; p < producers; p++) {
            String prefix = "P" + p + "-";
            List<Integer> sequence = received.stream().filter(id -> id.startsWith(prefix))
                    .map(id -> Integer.parseInt(id.substring(prefix.length()))).toList();
            assertThat(sequence).isSorted();
        }
        assertThat(buffer.snapshot().size()).isZero();
    }

    @Test
    void a_waiting_producer_can_be_interrupted_and_the_others_still_get_their_turn() throws Exception {
        ReceptionBuffer buffer = new ReceptionBuffer(1);
        buffer.put(message("A"));
        Future<?> interrupted = executor.submit(() -> {
            buffer.put(message("B"));
            return null;
        });
        Await.until("first producer blocked", () -> buffer.snapshot().blockedProducers() == 1);
        Future<?> survivor = executor.submit(() -> {
            buffer.put(message("C"));
            return null;
        });
        Await.until("both producers blocked", () -> buffer.snapshot().blockedProducers() == 2);

        interrupted.cancel(true);
        Await.until("interrupted producer gone", () -> buffer.snapshot().blockedProducers() == 1);
        buffer.take();
        survivor.get(5, TimeUnit.SECONDS);

        assertThat(buffer.take().truckId()).isEqualTo("C"); // B nunca entró: se interrumpió antes de tener espacio
    }

    @Test
    void a_waiting_consumer_can_be_interrupted() {
        ReceptionBuffer buffer = new ReceptionBuffer(1);
        Future<?> consumer = executor.submit(buffer::take);

        consumer.cancel(true);

        assertThat(consumer.isCancelled()).isTrue();
    }

    @Test
    void rejects_an_unusable_capacity_and_invalid_messages() {
        assertThatThrownBy(() -> new ReceptionBuffer(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReceptionMessage("TRK-1", "P-001", 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
