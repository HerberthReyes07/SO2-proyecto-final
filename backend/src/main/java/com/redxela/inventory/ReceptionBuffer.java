package com.redxela.inventory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Buffer acotado entre Recepción (productores) e Inventario (consumidor): el problema clásico de
 * productor/consumidor, implementado a mano con un lock y dos condiciones.
 *
 * <h3>Qué garantiza</h3>
 * <ul>
 *   <li><b>Sin pérdida de datos:</b> si el buffer está lleno, el productor <i>espera</i>; el mensaje
 *       nunca se descarta.</li>
 *   <li><b>Sin bloqueo indefinido:</b> el consumidor siempre progresa y no depende de nada que el
 *       productor retenga (los camiones sueltan sus recursos antes de reportar), así que el espacio
 *       siempre termina liberándose. Mientras tanto la espera es interrumpible.</li>
 *   <li><b>Orden:</b> los mensajes salen en el orden en que entraron (FIFO).</li>
 * </ul>
 *
 * <h3>Por qué dos condiciones</h3>
 * {@code notFull} la esperan los productores y {@code notEmpty} el consumidor. Con una sola, cada
 * {@code signal} podría despertar a alguien del otro grupo, que volvería a dormirse sin que el
 * despertar sirviera de nada.
 */
public final class ReceptionBuffer {

    private final int capacity;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();
    private final Deque<ReceptionMessage> queue = new ArrayDeque<>();

    // Contadores protegidos por `lock`.
    private int blockedProducers;
    private long accepted;
    private long delivered;

    public ReceptionBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    /**
     * Agrega un mensaje; si el buffer está lleno, espera hasta que haya espacio.
     *
     * @throws InterruptedException si el hilo es interrumpido mientras espera
     */
    public void put(ReceptionMessage message) throws InterruptedException {
        Objects.requireNonNull(message, "message");
        lock.lockInterruptibly();
        try {
            awaitSpace();
            queue.addLast(message);
            accepted++;
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retira el mensaje más antiguo; si el buffer está vacío, espera a que llegue uno.
     *
     * @throws InterruptedException si el hilo es interrumpido mientras espera
     */
    public ReceptionMessage take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (queue.isEmpty()) {
                try {
                    notEmpty.await();
                } catch (InterruptedException e) {
                    // Si me habían señalado y salgo sin tomar el mensaje, otro consumidor debe enterarse.
                    if (!queue.isEmpty()) {
                        notEmpty.signal();
                    }
                    throw e;
                }
            }
            ReceptionMessage message = queue.removeFirst();
            delivered++;
            notFull.signal();
            return message;
        } finally {
            lock.unlock();
        }
    }

    /** Foto consistente del estado del buffer. */
    public ReceptionBufferSnapshot snapshot() {
        lock.lock();
        try {
            return new ReceptionBufferSnapshot(capacity, queue.size(), blockedProducers, accepted, delivered);
        } finally {
            lock.unlock();
        }
    }

    private void awaitSpace() throws InterruptedException {
        if (queue.size() < capacity) {
            return;
        }
        blockedProducers++;
        try {
            while (queue.size() >= capacity) {
                notFull.await();
            }
        } catch (InterruptedException e) {
            // Si me habían señalado y salgo sin ocupar el espacio, otro productor debe enterarse
            // (si no, quedaría esperando con espacio libre: un despertar perdido).
            if (queue.size() < capacity) {
                notFull.signal();
            }
            throw e;
        } finally {
            blockedProducers--;
        }
    }
}
