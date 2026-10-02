package com.redxela.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.redxela.domain.ResourceType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Pruebas de concurrencia del pool: capacidad, prioridad, expulsión e interrupción. */
@Timeout(value = 20, unit = TimeUnit.SECONDS)
class ResourcePoolTest {

    private static final Comparator<Operation> BY_PRIORITY =
            Comparator.comparingInt(operation -> ((TestOperation) operation).priority);

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @AfterEach
    void shutdownThreads() {
        executor.shutdownNow();
    }

    @Test
    void never_grants_more_units_than_its_capacity() throws Exception {
        ResourcePool pool = new ResourcePool(ResourceType.FORKLIFT); // capacidad 3
        AtomicInteger inUse = new AtomicInteger();
        AtomicInteger maxInUse = new AtomicInteger();

        List<Future<?>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            TestOperation operation = new TestOperation("op-" + i, 0);
            tasks.add(executor.submit(() -> {
                pool.acquire(operation);
                int now = inUse.incrementAndGet();
                maxInUse.accumulateAndGet(now, Math::max);
                Thread.sleep(5);
                inUse.decrementAndGet();
                pool.release(operation);
                return null;
            }));
        }
        for (Future<?> task : tasks) {
            task.get();
        }

        assertThat(maxInUse.get()).isLessThanOrEqualTo(ResourceType.FORKLIFT.capacity());
        assertThat(pool.snapshot().holders()).isEmpty();
    }

    @Test
    void grants_released_unit_to_the_highest_priority_waiter() throws Exception {
        ResourcePool pool = new ResourcePool(ResourceType.SCANNER, 1, BY_PRIORITY);
        TestOperation holder = new TestOperation("holder", 0);
        pool.acquire(holder);

        List<String> grantOrder = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> tasks = new ArrayList<>();
        int[] arrivalPriorities = {3, 1, 2};
        for (int i = 0; i < arrivalPriorities.length; i++) {
            tasks.add(submitWaiter(pool, new TestOperation("p" + arrivalPriorities[i], arrivalPriorities[i]), grantOrder));
            int expectedWaiting = i + 1;
            awaitUntil(() -> pool.snapshot().waiting().size() == expectedWaiting);
        }

        pool.release(holder);
        for (Future<?> task : tasks) {
            task.get();
        }

        assertThat(grantOrder).containsExactly("p1", "p2", "p3");
    }

    @Test
    void breaks_priority_ties_by_arrival_order() throws Exception {
        ResourcePool pool = new ResourcePool(ResourceType.SCANNER); // FIFO
        TestOperation holder = new TestOperation("holder", 0);
        pool.acquire(holder);

        List<String> grantOrder = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> tasks = new ArrayList<>();
        for (String id : List.of("a", "b", "c")) {
            tasks.add(submitWaiter(pool, new TestOperation(id, 0), grantOrder));
            int expectedWaiting = tasks.size();
            awaitUntil(() -> pool.snapshot().waiting().size() == expectedWaiting);
        }

        pool.release(holder);
        for (Future<?> task : tasks) {
            task.get();
        }

        assertThat(grantOrder).containsExactly("a", "b", "c");
    }

    @Test
    void preempted_waiter_wakes_up_with_exception_and_leaves_the_queue() throws Exception {
        ResourcePool pool = new ResourcePool(ResourceType.SCANNER);
        pool.acquire(new TestOperation("holder", 0));
        TestOperation victim = new TestOperation("victim", 0);
        Future<?> victimTask = executor.submit(() -> {
            pool.acquire(victim);
            return null;
        });
        awaitUntil(() -> pool.snapshot().waiting().contains("victim"));

        boolean found = pool.preempt(victim);

        assertThat(found).isTrue();
        assertThatThrownBy(victimTask::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(OperationPreemptedException.class);
        assertThat(pool.snapshot().waiting()).isEmpty();
    }

    @Test
    void preempting_an_operation_that_is_not_waiting_returns_false() {
        ResourcePool pool = new ResourcePool(ResourceType.SCANNER);

        assertThat(pool.preempt(new TestOperation("nobody", 0))).isFalse();
    }

    @Test
    void interrupted_waiter_leaves_the_queue_and_the_next_one_still_gets_the_unit() throws Exception {
        ResourcePool pool = new ResourcePool(ResourceType.SCANNER);
        TestOperation holder = new TestOperation("holder", 0);
        pool.acquire(holder);

        Future<?> interrupted = executor.submit(() -> {
            pool.acquire(new TestOperation("interrupted", 0));
            return null;
        });
        awaitUntil(() -> pool.snapshot().waiting().contains("interrupted"));
        List<String> grantOrder = Collections.synchronizedList(new ArrayList<>());
        Future<?> next = submitWaiter(pool, new TestOperation("next", 0), grantOrder);
        awaitUntil(() -> pool.snapshot().waiting().contains("next"));

        interrupted.cancel(true);
        awaitUntil(() -> !pool.snapshot().waiting().contains("interrupted"));
        pool.release(holder);
        next.get();

        assertThat(grantOrder).containsExactly("next");
    }

    @Test
    void changing_the_waiting_order_reorders_the_queue() throws Exception {
        ResourcePool pool = new ResourcePool(ResourceType.SCANNER); // FIFO
        pool.acquire(new TestOperation("holder", 0));
        executor.submit(() -> {
            pool.acquire(new TestOperation("late-priority-2", 2));
            return null;
        });
        awaitUntil(() -> pool.snapshot().waiting().size() == 1);
        executor.submit(() -> {
            pool.acquire(new TestOperation("later-priority-1", 1));
            return null;
        });
        awaitUntil(() -> pool.snapshot().waiting().size() == 2);
        assertThat(pool.snapshot().waiting()).containsExactly("late-priority-2", "later-priority-1");

        pool.setWaitingOrder(BY_PRIORITY);

        assertThat(pool.snapshot().waiting()).containsExactly("later-priority-1", "late-priority-2");
    }

    @Test
    void releasing_a_unit_that_is_not_held_is_rejected() {
        ResourcePool pool = new ResourcePool(ResourceType.SCANNER);

        assertThatThrownBy(() -> pool.release(new TestOperation("x", 0)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acquiring_twice_from_the_same_pool_is_rejected() throws Exception {
        ResourcePool pool = new ResourcePool(ResourceType.FORKLIFT);
        TestOperation operation = new TestOperation("x", 0);
        pool.acquire(operation);

        assertThatThrownBy(() -> pool.acquire(operation)).isInstanceOf(IllegalStateException.class);
    }

    // ---- Utilidades de prueba ----

    /** Lanza un hilo que adquiere, registra el orden de concesión y libera. */
    private Future<?> submitWaiter(ResourcePool pool, TestOperation operation, List<String> grantOrder) {
        return executor.submit(() -> {
            pool.acquire(operation);
            grantOrder.add(operation.id());
            pool.release(operation);
            return null;
        });
    }

    /** Espera activa con tope de tiempo; evita {@code Thread.sleep} arbitrarios en las pruebas. */
    private static void awaitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Condition not met within 5 seconds");
            }
            Thread.sleep(2);
        }
    }

    /** Operación de prueba con prioridad numérica (menor = más urgente). */
    private static final class TestOperation implements Operation {
        private final String id;
        private final int priority;

        private TestOperation(String id, int priority) {
            this.id = id;
            this.priority = priority;
        }

        @Override
        public String id() {
            return id;
        }
    }
}
