package com.redxela.resources;

import com.redxela.domain.ResourceType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Pool de unidades idénticas de un recurso (ej. las 6 estaciones de empaque).
 *
 * <h3>Por qué no un {@code java.util.concurrent.Semaphore}</h3>
 * Un semáforo solo cuenta permisos: no sabe <i>quién</i> los tiene ni <i>quién</i> espera, y
 * entrega permisos en orden arbitrario. Aquí se necesita:
 * <ul>
 *   <li>saber los dueños y los que esperan (interfaz y detección de deadlock);</li>
 *   <li>conceder el recurso al mejor candidato según la política de atención;</li>
 *   <li>poder expulsar a quien espera (víctima de un deadlock).</li>
 * </ul>
 * Por eso se implementa como un <b>monitor</b>: un {@link ReentrantLock} protege todo el estado y
 * cada operación que espera tiene su propia {@link Condition}, de modo que al liberar una unidad
 * se despierta únicamente al siguiente en turno (sin <i>thundering herd</i>).
 *
 * <h3>Reglas</h3>
 * <ul>
 *   <li>Una operación retiene como máximo una unidad de cada pool.</li>
 *   <li>Todo el estado mutable se accede solo con {@code lock} tomado.</li>
 * </ul>
 */
public final class ResourcePool {

    private final ResourceType type;
    private final int capacity;
    private final ReentrantLock lock = new ReentrantLock();
    // Se señala cuando se libera una unidad; la usa quien necesita saber que hay capacidad (el despachador).
    private final Condition unitAvailable = lock.newCondition();
    private final List<Waiter> waiters = new ArrayList<>();
    private final Set<Operation> holders = new LinkedHashSet<>();
    private Comparator<Operation> waitingOrder;
    private long nextTicket;

    /** Crea un pool con la capacidad oficial del tipo y orden de llegada (FIFO). */
    public ResourcePool(ResourceType type) {
        this(type, type.capacity(), (a, b) -> 0);
    }

    /**
     * @param waitingOrder criterio de prioridad entre los que esperan; los empates se resuelven
     *                     por orden de llegada al pool, lo que evita inanición entre iguales
     */
    public ResourcePool(ResourceType type, int capacity, Comparator<Operation> waitingOrder) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.type = Objects.requireNonNull(type, "type");
        this.capacity = capacity;
        this.waitingOrder = Objects.requireNonNull(waitingOrder, "waitingOrder");
    }

    public ResourceType type() {
        return type;
    }

    /**
     * Bloquea al hilo llamador hasta que se le conceda una unidad.
     *
     * @throws InterruptedException         si el hilo es interrumpido mientras espera
     * @throws OperationPreemptedException  si la operación fue expulsada de la espera ({@link #preempt})
     * @throws IllegalStateException        si la operación ya retiene una unidad de este pool
     */
    public void acquire(Operation operation) throws InterruptedException, OperationPreemptedException {
        Objects.requireNonNull(operation, "operation");
        lock.lock();
        try {
            if (holders.contains(operation)) {
                throw new IllegalStateException(operation.id() + " already holds a unit of " + type);
            }
            Waiter self = new Waiter(operation, nextTicket++, lock.newCondition());
            waiters.add(self);
            try {
                awaitTurn(self);
                holders.add(operation);
            } finally {
                // Se ejecuta tanto si se concedió la unidad como si hubo interrupción o expulsión:
                // en ambos casos hay que salir de la fila y dejar que el siguiente reevalúe.
                waiters.remove(self);
                signalNextWaiter();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Devuelve la unidad que retiene la operación y despierta al siguiente en turno.
     *
     * @throws IllegalStateException si la operación no retenía ninguna unidad
     */
    public void release(Operation operation) {
        lock.lock();
        try {
            if (!holders.remove(operation)) {
                throw new IllegalStateException(operation.id() + " does not hold a unit of " + type);
            }
            signalNextWaiter();
            unitAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Bloquea hasta que haya al menos una unidad libre, <b>sin tomarla</b>.
     *
     * <p>Permite al despachador esperar capacidad sin ocupar un recurso y sin comprometerse todavía
     * con ningún pedido. Solo es fiable si quien la usa es el único que adquiere de este pool (como
     * ocurre con los encargados de bodega); de lo contrario la unidad podría ocuparse entre la
     * espera y el {@link #acquire}, que en ese caso simplemente esperaría un momento.
     *
     * @throws InterruptedException si el hilo es interrumpido mientras espera
     */
    public void awaitAvailableUnit() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (holders.size() >= capacity) {
                unitAvailable.await();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Expulsa a una operación de la fila de espera: su hilo despertará lanzando
     * {@link OperationPreemptedException}. No quita recursos ya retenidos; eso lo hace la operación
     * al manejar la excepción.
     *
     * @return {@code true} si la operación estaba esperando en este pool
     */
    public boolean preempt(Operation operation) {
        lock.lock();
        try {
            for (Waiter waiter : waiters) {
                if (waiter.operation.equals(operation)) {
                    waiter.preempted = true;
                    waiter.turn.signal();
                    return true;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /** Cambia en caliente el criterio de prioridad (cambio de política de atención). */
    public void setWaitingOrder(Comparator<Operation> newOrder) {
        Objects.requireNonNull(newOrder, "newOrder");
        lock.lock();
        try {
            this.waitingOrder = newOrder;
            signalNextWaiter(); // el mejor candidato pudo cambiar
        } finally {
            lock.unlock();
        }
    }

    /** Foto consistente del estado actual del pool. */
    public PoolSnapshot snapshot() {
        lock.lock();
        try {
            List<String> holderIds = holders.stream().map(Operation::id).toList();
            List<String> waitingIds = waiters.stream()
                    .sorted(turnOrder())
                    .map(waiter -> waiter.operation.id())
                    .toList();
            return new PoolSnapshot(type, capacity, holderIds, waitingIds);
        } finally {
            lock.unlock();
        }
    }

    // ---- Internos: todos se invocan con `lock` tomado ----

    /** Espera (liberando el lock) hasta que haya unidad libre y el hilo sea el primero en turno. */
    private void awaitTurn(Waiter self) throws InterruptedException, OperationPreemptedException {
        while (!self.preempted && !canGrant(self)) {
            self.turn.await();
        }
        if (self.preempted) {
            throw new OperationPreemptedException(self.operation.id(), type);
        }
    }

    private boolean canGrant(Waiter candidate) {
        return holders.size() < capacity && bestWaiter().filter(best -> best == candidate).isPresent();
    }

    /** Despierta al siguiente en turno solo si realmente hay una unidad libre para él. */
    private void signalNextWaiter() {
        if (holders.size() < capacity) {
            bestWaiter().ifPresent(waiter -> waiter.turn.signal());
        }
    }

    /** Mejor candidato según la política; los ya expulsados se ignoran porque van a salir. */
    private Optional<Waiter> bestWaiter() {
        return waiters.stream().filter(waiter -> !waiter.preempted).min(turnOrder());
    }

    private Comparator<Waiter> turnOrder() {
        return Comparator.comparing((Waiter waiter) -> waiter.operation, waitingOrder)
                .thenComparingLong(waiter -> waiter.ticket);
    }

    /** Registro de una operación que espera en la fila; su estado lo protege el lock del pool. */
    private static final class Waiter {
        private final Operation operation;
        private final long ticket;
        private final Condition turn;
        private boolean preempted;

        private Waiter(Operation operation, long ticket, Condition turn) {
            this.operation = operation;
            this.ticket = ticket;
            this.turn = turn;
        }
    }
}
