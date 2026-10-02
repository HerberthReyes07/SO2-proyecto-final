package com.redxela.scheduling;

import com.redxela.domain.QueuedOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Lista de espera de pedidos que aún no tienen encargado asignado.
 *
 * <p>Es una lista simple y no una cola de prioridad a propósito: la política puede cambiarse en
 * caliente, y una estructura que mantiene su orden al insertar tendría que reconstruirse. Con una
 * lista, elegir al mejor es un recorrido (O(n)) con la política vigente en ese instante, y el
 * cambio de política es inmediato.
 *
 * <h3>Sincronización</h3>
 * Un {@link ReentrantLock} protege la lista y la política. El despachador, único consumidor,
 * espera en la {@link Condition} {@code notEmpty} hasta que llegue algún pedido.
 */
public final class WaitingList {

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final List<QueuedOrder> entries = new ArrayList<>();
    private SchedulingPolicy policy;

    public WaitingList(SchedulingPolicy initialPolicy) {
        this.policy = Objects.requireNonNull(initialPolicy, "initialPolicy");
    }

    /** Agrega un pedido y despierta al despachador si estaba esperando. */
    public void add(QueuedOrder queued) {
        Objects.requireNonNull(queued, "queued");
        lock.lock();
        try {
            entries.add(queued);
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Bloquea hasta que haya al menos un pedido. No lo retira: la elección del mejor se hace
     * después, con {@link #takeBest()}, lo más tarde posible.
     */
    public void awaitNotEmpty() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (entries.isEmpty()) {
                notEmpty.await();
            }
        } finally {
            lock.unlock();
        }
    }

    /** Retira y devuelve el pedido que la política vigente atendería primero, si hay alguno. */
    public Optional<QueuedOrder> takeBest() {
        lock.lock();
        try {
            Optional<QueuedOrder> best = entries.stream().min(policy.comparator());
            best.ifPresent(entries::remove);
            return best;
        } finally {
            lock.unlock();
        }
    }

    /** Copia de los pedidos en espera, en el orden en que la política vigente los atendería. */
    public List<QueuedOrder> snapshot() {
        lock.lock();
        try {
            return entries.stream().sorted(policy.comparator()).toList();
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return entries.size();
        } finally {
            lock.unlock();
        }
    }

    public SchedulingPolicy policy() {
        lock.lock();
        try {
            return policy;
        } finally {
            lock.unlock();
        }
    }

    /** Cambia la política en caliente; afecta a la siguiente elección y a {@link #snapshot()}. */
    public void setPolicy(SchedulingPolicy newPolicy) {
        Objects.requireNonNull(newPolicy, "newPolicy");
        lock.lock();
        try {
            this.policy = newPolicy;
        } finally {
            lock.unlock();
        }
    }
}
