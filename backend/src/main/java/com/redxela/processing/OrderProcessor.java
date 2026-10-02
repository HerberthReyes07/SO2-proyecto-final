package com.redxela.processing;

import com.redxela.domain.Order;
import com.redxela.resources.OperationPreemptedException;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lanza un hilo real por cada pedido y mantiene el registro de los que están en proceso.
 *
 * <p>Un {@code ConcurrentHashMap} basta como registro: cada pedido se inserta antes de que su hilo
 * arranque y se quita al terminar, y los lectores (snapshots) solo necesitan una vista aproximada
 * del conjunto, no una foto transaccional.
 */
public final class OrderProcessor implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(OrderProcessor.class);

    private final OrderWorkflow workflow;
    private final ProcessingTimes times;
    private final ExecutorService threads =
            Executors.newThreadPerTaskExecutor(Thread.ofPlatform().name("order-", 1).factory());
    private final Map<String, OrderExecution> active = new ConcurrentHashMap<>();
    private final AtomicLong arrivals = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();

    public OrderProcessor(OrderWorkflow workflow, ProcessingTimes times) {
        this.workflow = workflow;
        this.times = times;
    }

    /** Empieza a procesar el pedido en su propio hilo; retorna de inmediato. */
    public void submit(Order order) {
        OrderExecution execution = new OrderExecution(
                order, ExecutionPlan.forOrder(order, times), arrivals.incrementAndGet());
        active.put(order.id(), execution);
        try {
            threads.execute(() -> runAndTrack(execution));
        } catch (RuntimeException e) {
            active.remove(order.id()); // no se pudo lanzar el hilo (ej. procesador cerrado)
            throw e;
        }
    }

    /** Pedidos en proceso en el instante simulado {@code now}, en orden de llegada. */
    public List<OrderProgressSnapshot> activeOrders(Duration now) {
        return active.values().stream()
                .sorted(Comparator.comparingLong(OrderExecution::sequence))
                .map(execution -> execution.progress(now))
                .toList();
    }

    public long completedCount() {
        return completed.get();
    }

    private void runAndTrack(OrderExecution execution) {
        try {
            workflow.run(execution);
            completed.incrementAndGet();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // el procesador se está cerrando
            LOG.info("Order {} interrupted", execution.id());
        } catch (OperationPreemptedException e) {
            // Aún no hay resolución de deadlock; cuando exista, el pedido volverá a la lista de espera.
            LOG.warn("Order {} was preempted: {}", execution.id(), e.getMessage());
        } catch (RuntimeException e) {
            LOG.error("Order {} failed", execution.id(), e);
        } finally {
            active.remove(execution.id());
        }
    }

    @Override
    public void close() {
        threads.shutdownNow();
    }
}
