package com.redxela.inventory;

import com.redxela.sim.SimClock;
import com.redxela.warehouse.StoreResult;
import com.redxela.warehouse.Warehouse;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hilo de Inventario: retira los mensajes de Recepción a su propio ritmo, asigna las ubicaciones
 * del almacén, actualiza las existencias y avisa que cambió el stock de ese producto para que se
 * reevalúen los pedidos que lo esperaban.
 *
 * <p>Es el único que modifica las existencias por recepciones, y lo hace por medio de las API de
 * {@link Inventory} y {@link Warehouse}: Recepción nunca las toca directamente.
 *
 * <h3>Orden: primero el almacén, después el inventario</h3>
 * Lo que no cabe en el almacén se rechaza y <b>no</b> se suma al inventario; por eso se almacena
 * primero y se suma solo lo almacenado. Así las existencias nunca superan lo que hay físicamente.
 * (Esperar a que se libere espacio sería un bloqueo indefinido: el espacio solo se libera cuando
 * los pedidos consumen stock, y puede que ese stock no exista.)
 */
public final class InventoryWorker implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(InventoryWorker.class);
    private static final long SHUTDOWN_WAIT_MILLIS = 1_000;

    private final ReceptionBuffer buffer;
    private final Inventory inventory;
    private final Warehouse warehouse;
    private final SimClock clock;
    private final InventoryTimes times;
    private final Consumer<String> onStockChanged;
    private final AtomicLong processed = new AtomicLong();
    private volatile ReceptionMessage processing;
    private Thread thread;

    /**
     * @param onStockChanged se invoca con el id del producto después de sumar sus unidades al inventario
     */
    public InventoryWorker(ReceptionBuffer buffer, Inventory inventory, Warehouse warehouse, SimClock clock,
                           InventoryTimes times, Consumer<String> onStockChanged) {
        this.buffer = buffer;
        this.inventory = inventory;
        this.warehouse = warehouse;
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
            store(message);
            processed.incrementAndGet();
        } catch (IllegalArgumentException e) {
            // Un mensaje inválido (producto desconocido) se descarta con aviso, no detiene a Inventario.
            LOG.error("Discarding invalid reception message {}: {}", message, e.getMessage());
        } finally {
            processing = null;
        }
    }

    private void store(ReceptionMessage message) {
        StoreResult result = warehouse.store("LOT-" + message.truckId(), message.productId(), message.quantity());
        if (result.rejected() > 0) {
            LOG.warn("Warehouse is full: {} of {} units of {} from {} were rejected",
                    result.rejected(), result.requested(), message.productId(), message.truckId());
        }
        if (result.stored() > 0) {
            inventory.receive(message.productId(), result.stored());
            onStockChanged.accept(message.productId());
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
