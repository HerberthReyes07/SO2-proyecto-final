package com.redxela.processing;

import com.redxela.domain.CompletedOrder;
import com.redxela.domain.OrderStatus;
import com.redxela.domain.QueuedOrder;
import com.redxela.resources.OperationPreemptedException;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ejecuta los pedidos ya admitidos, cada uno en su propio hilo real, y mantiene el registro de los
 * que están en proceso. No decide cuándo admitir un pedido: eso es del despachador.
 *
 * <p>Un {@code ConcurrentHashMap} basta como registro: cada pedido se inserta antes de que su hilo
 * arranque y se quita al terminar, y los lectores (snapshots) solo necesitan una vista aproximada
 * del conjunto, no una foto transaccional.
 */
public final class OrderProcessor implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(OrderProcessor.class);
    private static final long SHUTDOWN_WAIT_MILLIS = 2_000;

    private final OrderWorkflow workflow;
    private final ProcessingTimes times;
    private final ExecutorService threads =
            Executors.newThreadPerTaskExecutor(Thread.ofPlatform().name("order-", 1).factory());
    private final Map<String, OrderExecution> active = new ConcurrentHashMap<>();
    private final AtomicLong completed = new AtomicLong();
    // Se recorre desde los hilos de los pedidos y se modifica rara vez (al armar la aplicación).
    private final List<OrderLifecycleListener> listeners = new CopyOnWriteArrayList<>();
    // Quién recibe a los pedidos expulsados por un deadlock; se asigna una vez al armar la aplicación.
    private volatile Consumer<QueuedOrder> expelledHandler = queued ->
            LOG.warn("Order {} was expelled but nobody takes it back", queued.order().id());

    public OrderProcessor(OrderWorkflow workflow, ProcessingTimes times) {
        this.workflow = workflow;
        this.times = times;
    }

    /**
     * Registra a alguien que quiere enterarse de cómo termina cada pedido (ej. las estadísticas o el
     * inventario). Se invoca en el hilo del pedido, así que debe ser rápido y seguro entre hilos.
     */
    public void addListener(OrderLifecycleListener listener) {
        listeners.add(listener);
    }

    /**
     * Indica adónde van los pedidos expulsados para deshacer un deadlock (la lista de espera). Un pedido
     * expulsado ya liberó todo lo que retenía, incluido el encargado y su stock reservado.
     */
    public void onExpelled(Consumer<QueuedOrder> handler) {
        this.expelledHandler = handler;
    }

    /** Crea la ejecución de un pedido que salió de la lista de espera, con su plan de etapas. */
    public OrderExecution newExecution(QueuedOrder queued) {
        return new OrderExecution(queued, ExecutionPlan.forOrder(queued.order(), times));
    }

    /**
     * Empieza a ejecutar el pedido en su propio hilo; retorna de inmediato.
     *
     * @param execution ejecución que ya tiene un encargado asignado ({@link OrderWorkflow#assignWorker})
     * @throws java.util.concurrent.RejectedExecutionException si el procesador ya fue cerrado
     */
    public void start(OrderExecution execution) {
        active.put(execution.id(), execution);
        try {
            threads.execute(() -> runAndTrack(execution));
        } catch (RuntimeException e) {
            active.remove(execution.id()); // no se pudo lanzar el hilo
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
            CompletedOrder summary = workflow.run(execution);
            completed.incrementAndGet();
            notifyCompleted(summary);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // el procesador se está cerrando
            LOG.info("Order {} interrupted", execution.id());
            notifyAborted(execution.id());
        } catch (OperationPreemptedException e) {
            // Víctima de un deadlock: ya liberó todo; devuelve su stock y vuelve a la lista de espera.
            LOG.info("Order {} was expelled while waiting for {}; returning it to the waiting list",
                    execution.id(), e.awaitedResource());
            execution.order().status(OrderStatus.WAITING);
            notifyAborted(execution.id());
            expelledHandler.accept(execution.queued());
        } catch (RuntimeException e) {
            LOG.error("Order {} failed", execution.id(), e);
            notifyAborted(execution.id());
        } finally {
            active.remove(execution.id());
        }
    }

    private void notifyCompleted(CompletedOrder summary) {
        for (OrderLifecycleListener listener : listeners) {
            try {
                listener.onCompleted(summary);
            } catch (RuntimeException e) {
                // Un listener defectuoso no debe afectar al pedido ni a los demás listeners.
                LOG.error("Completion listener failed for {}", summary.orderId(), e);
            }
        }
    }

    private void notifyAborted(String orderId) {
        for (OrderLifecycleListener listener : listeners) {
            try {
                listener.onAborted(orderId);
            } catch (RuntimeException e) {
                LOG.error("Abort listener failed for {}", orderId, e);
            }
        }
    }

    @Override
    public void close() {
        threads.shutdownNow();
        // Se espera a que los hilos terminen: quien cierra un procesador (p. ej. el comparador, que arma y desarma
        // uno por política) necesita la garantía de que no queda ningún pedido corriendo en segundo plano.
        try {
            if (!threads.awaitTermination(SHUTDOWN_WAIT_MILLIS, TimeUnit.MILLISECONDS)) {
                LOG.warn("Some order threads did not stop within {} ms", SHUTDOWN_WAIT_MILLIS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
