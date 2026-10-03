package com.redxela.processing;

import com.redxela.domain.Order;
import com.redxela.domain.QueuedOrder;
import com.redxela.inventory.Inventory;
import com.redxela.resources.ResourceRegistry;
import com.redxela.scheduling.SchedulingPolicy;
import com.redxela.scheduling.WaitingList;
import com.redxela.sim.SimClock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Planificador: recibe los pedidos nuevos, los mantiene en la lista de espera y administra la
 * política de atención. Es la fachada que usa el resto de la aplicación; el hilo despachador
 * queda como detalle interno.
 */
public final class OrderScheduler implements AutoCloseable {

    /** Política con la que arranca el sistema: el enunciado dice que el nivel de servicio determina qué tan pronto se atiende. */
    public static final SchedulingPolicy DEFAULT_POLICY = SchedulingPolicy.STRICT_PRIORITY;

    /** Máximo de pedidos en espera que viajan en cada snapshot. */
    static final int MAX_WAITING_IN_SNAPSHOT = 50;

    private static final long SHUTDOWN_WAIT_MILLIS = 1_000;

    private final SimClock clock;
    private final ResourceRegistry resources;
    private final Inventory inventory;
    private final WaitingList waitingList = new WaitingList(DEFAULT_POLICY);
    private final AtomicLong arrivals = new AtomicLong();
    private final Dispatcher dispatcher;
    private Thread dispatcherThread;

    public OrderScheduler(SimClock clock, ResourceRegistry resources, OrderWorkflow workflow,
                          OrderProcessor processor, Inventory inventory) {
        this.clock = clock;
        this.resources = resources;
        this.inventory = inventory;
        this.dispatcher = new Dispatcher(waitingList, workflow, processor, inventory);
        resources.setWaitingOrder(OperationOrdering.of(DEFAULT_POLICY));
    }

    /** Arranca el hilo despachador. */
    public synchronized void start() {
        if (dispatcherThread == null) {
            dispatcherThread = Thread.ofPlatform().name("dispatcher").start(dispatcher);
        }
    }

    /** Un pedido nuevo entra a la lista de espera, con la hora simulada actual como hora de llegada. */
    public void enqueue(Order order) {
        waitingList.add(new QueuedOrder(order, clock.now(), arrivals.incrementAndGet()));
    }

    /**
     * Avisa que cambió el stock de un producto: los pedidos a los que les faltaba pueden haber pasado a
     * ser atendibles, así que se despierta al despachador para que vuelva a evaluarlos.
     */
    public void stockChanged() {
        waitingList.reevaluate();
    }

    public SchedulingPolicy policy() {
        return waitingList.policy();
    }

    /**
     * Cambia la política de atención en caliente: afecta tanto a la lista de espera como a la
     * fila de cada recurso compartido.
     */
    public void setPolicy(SchedulingPolicy policy) {
        waitingList.setPolicy(policy);
        resources.setWaitingOrder(OperationOrdering.of(policy));
    }

    /** Estado de la planificación en el instante simulado {@code now}. */
    public SchedulingSnapshot snapshot(Duration now) {
        // Primero la política y luego la lista: si la política cambia entre ambas lecturas, la lista
        // se ve con el orden nuevo; una discrepancia de un instante que el siguiente snapshot corrige.
        SchedulingPolicy policy = waitingList.policy();
        List<QueuedOrder> waiting = waitingList.snapshot();
        List<WaitingOrderSnapshot> visible = waiting.stream()
                .limit(MAX_WAITING_IN_SNAPSHOT)
                .map(queued -> toSnapshot(queued, now))
                .toList();
        return new SchedulingSnapshot(policy, waiting.size(), visible);
    }

    private WaitingOrderSnapshot toSnapshot(QueuedOrder queued, Duration now) {
        Order order = queued.order();
        return new WaitingOrderSnapshot(
                order.id(),
                order.customer().name(),
                order.serviceLevel(),
                order.totalItems(),
                queued.waitedAt(now).toMillis(),
                order.serviceLevel().targetWait().toMillis(),
                inventory.missingFor(order));
    }

    /** Detiene el hilo despachador. */
    @Override
    public void close() {
        Thread thread;
        synchronized (this) {
            thread = dispatcherThread;
        }
        if (thread == null) {
            return;
        }
        thread.interrupt();
        try {
            thread.join(SHUTDOWN_WAIT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
