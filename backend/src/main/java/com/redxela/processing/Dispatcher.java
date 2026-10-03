package com.redxela.processing;

import com.redxela.domain.QueuedOrder;
import com.redxela.inventory.Inventory;
import com.redxela.resources.OperationPreemptedException;
import com.redxela.scheduling.WaitingList;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hilo que admite pedidos: toma pedidos de la lista de espera y los pone en proceso a medida que
 * hay encargados libres <b>y stock</b> para atenderlos.
 *
 * <h3>Decisión tardía</h3>
 * El despachador espera primero a que haya <i>un encargado libre</i> y <i>algún pedido elegible</i>,
 * y solo entonces elige al mejor según la política. Si eligiera antes y luego esperara, un pedido
 * Exprés que llegara mientras tanto quedaría detrás de uno ya elegido. Las esperas no ocupan
 * recursos: mientras no hay encargado, el despachador no retiene ninguno.
 *
 * <h3>Elegible = hay stock para todo el pedido</h3>
 * Un pedido al que le falta stock se salta (el siguiente mejor pasa adelante) y se reevalúa cuando
 * Inventario procesa una llegada. Al admitir, se reserva todo su stock de una vez, así que ningún
 * pedido empieza a procesarse con la mercadería a medias.
 *
 * <p>Es el único que asigna encargados y reserva stock, por lo que entre "hay encargado y stock" y
 * "lo tomo" nadie puede quitárselos: el stock solo puede aumentar en ese intervalo.
 */
final class Dispatcher implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(Dispatcher.class);
    private static final long ERROR_BACKOFF_MILLIS = 100;

    private final WaitingList waitingList;
    private final OrderWorkflow workflow;
    private final OrderProcessor processor;
    private final Inventory inventory;
    private final Predicate<QueuedOrder> hasStock;

    Dispatcher(WaitingList waitingList, OrderWorkflow workflow, OrderProcessor processor, Inventory inventory) {
        this.waitingList = waitingList;
        this.workflow = workflow;
        this.processor = processor;
        this.inventory = inventory;
        this.hasStock = queued -> inventory.canFulfill(queued.order());
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    dispatchNext();
                } catch (RuntimeException e) {
                    // Un error inesperado no debe matar al despachador en silencio: se registra y se
                    // reintenta tras una pausa breve para no girar sin parar si el error persiste.
                    LOG.error("Dispatcher iteration failed", e);
                    Thread.sleep(ERROR_BACKOFF_MILLIS);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.info("Dispatcher stopped");
        }
    }

    private void dispatchNext() throws InterruptedException {
        workflow.awaitFreeWorker();
        waitingList.awaitEligible(hasStock);
        // Solo el despachador retira pedidos, así que sigue habiendo uno elegible; el Optional vacío
        // solo es posible si ese supuesto se rompe, y en ese caso simplemente se reintenta.
        waitingList.takeBest(hasStock).ifPresent(this::admit);
    }

    private void admit(QueuedOrder queued) {
        if (!inventory.reserve(queued.order())) {
            LOG.warn("Stock for {} disappeared before reserving it; returning it to the waiting list", queued.order().id());
            waitingList.add(queued);
            return;
        }
        OrderExecution execution = processor.newExecution(queued);
        try {
            workflow.assignWorker(execution);
        } catch (InterruptedException e) {
            giveBack(queued);
            Thread.currentThread().interrupt();
            return;
        } catch (OperationPreemptedException e) {
            LOG.warn("Worker assignment for {} was preempted; returning it to the waiting list", queued.order().id());
            giveBack(queued);
            return;
        }
        try {
            processor.start(execution);
        } catch (RuntimeException e) {
            // Solo ocurre si el procesador ya se cerró. Se devuelven el encargado, el stock y el pedido
            // a la lista para no perderlo y se detiene el despachador: si siguiera, volvería a tomar el
            // mismo pedido de inmediato y giraría sin parar.
            LOG.warn("Could not start {}; stopping the dispatcher", queued.order().id(), e);
            workflow.unassignWorker(execution);
            giveBack(queued);
            Thread.currentThread().interrupt();
        }
    }

    /** Devuelve el pedido a la lista y su stock al inventario. */
    private void giveBack(QueuedOrder queued) {
        inventory.release(queued.order().id());
        waitingList.add(queued);
    }
}
