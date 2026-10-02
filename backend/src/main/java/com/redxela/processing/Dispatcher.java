package com.redxela.processing;

import com.redxela.domain.QueuedOrder;
import com.redxela.resources.OperationPreemptedException;
import com.redxela.scheduling.WaitingList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hilo que admite pedidos: toma pedidos de la lista de espera y los pone en proceso a medida que
 * hay encargados libres.
 *
 * <h3>Decisión tardía</h3>
 * El despachador espera primero a que haya <i>algún pedido</i> y <i>algún encargado libre</i>, y
 * solo entonces elige al mejor según la política. Si eligiera antes y luego esperara al encargado,
 * un pedido Exprés que llegara mientras tanto quedaría detrás de uno ya elegido. Las esperas no
 * ocupan recursos: mientras no hay encargado, el despachador no retiene ninguno.
 *
 * <p>Es el único que asigna encargados, por lo que entre "hay uno libre" y "lo tomo" nadie más
 * puede quitárselo.
 */
final class Dispatcher implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(Dispatcher.class);

    private final WaitingList waitingList;
    private final OrderWorkflow workflow;
    private final OrderProcessor processor;

    Dispatcher(WaitingList waitingList, OrderWorkflow workflow, OrderProcessor processor) {
        this.waitingList = waitingList;
        this.workflow = workflow;
        this.processor = processor;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                dispatchNext();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.info("Dispatcher stopped");
        }
    }

    private void dispatchNext() throws InterruptedException {
        waitingList.awaitNotEmpty();
        workflow.awaitFreeWorker();
        // Solo el despachador retira pedidos, así que la lista sigue sin estar vacía; el Optional
        // vacío solo es posible si ese supuesto se rompe, y en ese caso simplemente se reintenta.
        waitingList.takeBest().ifPresent(this::admit);
    }

    private void admit(QueuedOrder queued) {
        OrderExecution execution = processor.newExecution(queued);
        try {
            workflow.assignWorker(execution);
        } catch (InterruptedException e) {
            waitingList.add(queued); // no se pierde el pedido si se interrumpe justo aquí
            Thread.currentThread().interrupt();
            return;
        } catch (OperationPreemptedException e) {
            LOG.warn("Worker assignment for {} was preempted; returning it to the waiting list", queued.order().id());
            waitingList.add(queued);
            return;
        }
        try {
            processor.start(execution);
        } catch (RuntimeException e) {
            // Solo ocurre si el procesador ya se cerró. Se devuelve el encargado y el pedido a la
            // lista para no perderlo y se detiene el despachador: si siguiera, volvería a tomar el
            // mismo pedido de inmediato y giraría sin parar.
            LOG.warn("Could not start {}; stopping the dispatcher", queued.order().id(), e);
            workflow.unassignWorker(execution);
            waitingList.add(queued);
            Thread.currentThread().interrupt();
        }
    }
}
