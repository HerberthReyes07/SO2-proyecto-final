package com.redxela.processing;

import com.redxela.domain.OrderStatus;
import com.redxela.domain.ResourceType;
import com.redxela.domain.Stage;
import com.redxela.resources.OperationPreemptedException;
import com.redxela.resources.ResourceRegistry;
import com.redxela.sim.SimClock;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Algoritmo que ejecuta un pedido: adquiere recursos, trabaja y los libera. Se ejecuta en el hilo
 * que lo invoca (el hilo del pedido) y no sabe nada de cómo se crean ni se rastrean los hilos.
 *
 * <h3>Modelo de retención: traspaso entre etapas</h3>
 * <ul>
 *   <li>El encargado se retiene durante todo el pedido.</li>
 *   <li>Dentro de una etapa, los recursos se piden en orden global y se <b>retienen mientras se
 *       espera el siguiente</b> (<i>hold-and-wait</i>).</li>
 *   <li>Al pasar de etapa, primero se adquieren los recursos de la siguiente y solo entonces se
 *       sueltan los de la anterior: la mercancía no puede quedar "en el aire" (ej. el montacargas
 *       no se libera hasta que la estación de empaque aceptó el pedido).</li>
 * </ul>
 * Los recursos nunca se piden en orden descendente, por lo que entre pedidos no hay espera circular.
 */
public final class OrderWorkflow {

    private final ResourceRegistry resources;
    private final SimClock clock;

    public OrderWorkflow(ResourceRegistry resources, SimClock clock) {
        this.resources = resources;
        this.clock = clock;
    }

    /**
     * Ejecuta el pedido hasta completarlo. Pase lo que pase (interrupción, expulsión o error)
     * libera todo lo que retiene antes de salir.
     */
    public void run(OrderExecution execution) throws InterruptedException, OperationPreemptedException {
        Set<ResourceType> held = EnumSet.noneOf(ResourceType.class);
        try {
            acquire(execution, Stage.PICKING, ResourceType.WAREHOUSE_WORKER, held);
            execution.order().status(OrderStatus.PROCESSING);

            List<ResourceType> previousStageResources = List.of();
            for (StagePlan stage : execution.plan().stages()) {
                for (ResourceType resource : stage.resources()) {
                    acquire(execution, stage.stage(), resource, held);
                }
                release(execution, previousStageResources, held); // traspaso completado
                work(execution, stage);
                previousStageResources = stage.resources();
            }
        } finally {
            release(execution, List.copyOf(held), held);
        }
        // Solo se llega aquí si no hubo excepción, y ya con todos los recursos liberados.
        execution.order().status(OrderStatus.COMPLETED);
    }

    private void acquire(OrderExecution execution, Stage stage, ResourceType type, Set<ResourceType> held)
            throws InterruptedException, OperationPreemptedException {
        execution.beginWaiting(stage, type);
        resources.pool(type).acquire(execution);
        held.add(type);
        execution.resourceAcquired(type);
    }

    private void release(OrderExecution execution, List<ResourceType> toRelease, Set<ResourceType> held) {
        for (ResourceType type : toRelease) {
            resources.pool(type).release(execution);
            held.remove(type);
            execution.resourceReleased(type);
        }
    }

    private void work(OrderExecution execution, StagePlan stage) throws InterruptedException {
        execution.beginWork(stage.stage(), stage.work(), clock.now());
        clock.sleep(stage.work());
        execution.finishStageWork();
    }
}
