package com.redxela.inventory;

import com.redxela.sim.SimClock;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hilo de Inventario: retira los mensajes de Recepción a su propio ritmo, actualiza las existencias
 * y avisa que cambió el stock de ese producto para que se reevalúen los pedidos que lo esperaban.
 *
 * <p>Es el único que modifica las existencias por recepciones, y lo hace por medio de la API de
 * {@link Inventory}: Recepción nunca toca el inventario directamente.
 */
public final class InventoryWorker implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(InventoryWorker.class);
    private static final long SHUTDOWN_WAIT_MILLIS = 1_000;

    private final ReceptionBuffer buffer;
    private final Inventory inventory;
    private final SimClock clock;
    private final InventoryTimes times;
    private final Consumer<String> onStockChanged;
    private final AtomicLong processed = new AtomicLong();
    private volatile ReceptionMessage processing;
    private Thread thread;

    /**
     * @param onStockChanged se invoca con el id del producto después de sumar sus unidades al inventario
     */
    public InventoryWorker(ReceptionBuffer buffer, Inventory inventory, SimClock clock, InventoryTimes times,
                           Consumer<String> onStockChanged) {
        this.buffer = buffer;
        this.inventory = inventory;
        this.clock = clock;
        this.times = times;
        this.onStockChanged = onStockChanged;
    }

    public synchronized void start() {
        if (thread == null) {
            thread = Thread.ofPlatform().name("inventory-worker").start(this::run);
        }
    }

    public InventoryWorkerSnapshot snapshot() {
        return new InventoryWorkerSnapshot(processing, processed.get());
    }

    private void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                processNext();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.info("Inventory worker stopped");
        }
    }

    private void processNext() throws InterruptedException {
        ReceptionMessage message = buffer.take();
        processing = message;
        try {
            clock.sleep(times.processingTime(message.quantity()));
            inventory.receive(message.productId(), message.quantity());
            processed.incrementAndGet();
            onStockChanged.accept(message.productId());
        } catch (IllegalArgumentException e) {
            // Un mensaje inválido (producto desconocido) se descarta con aviso, no detiene a Inventario.
            LOG.error("Discarding invalid reception message {}: {}", message, e.getMessage());
        } finally {
            processing = null;
        }
    }

    @Override
    public void close() {
        Thread running;
        synchronized (this) {
            running = thread;
        }
        if (running == null) {
            return;
        }
        running.interrupt();
        try {
            running.join(SHUTDOWN_WAIT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
