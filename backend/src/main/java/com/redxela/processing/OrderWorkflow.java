package com.redxela.processing;

import com.redxela.domain.CompletedOrder;
import com.redxela.domain.OrderStatus;
import com.redxela.domain.ResourceType;
import com.redxela.domain.Stage;
import com.redxela.resources.OperationPreemptedException;
import com.redxela.resources.ResourcePool;
import com.redxela.resources.ResourceRegistry;
import com.redxela.sim.SimClock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Lógica de recursos de un pedido: asignación del encargado y ejecución de sus etapas. No sabe
 * nada de cómo se crean ni se rastrean los hilos.
 *
 * <h3>El encargado lo asigna el despachador</h3>
 * El encargado es lo que limita cuántos pedidos están en proceso a la vez, y quién lo recibe es una
 * decisión de planificación. Por eso el despachador lo asigna ({@link #assignWorker}) y recién
 * entonces el pedido ejecuta sus etapas ({@link #run}); el hilo del pedido nunca compite por él.
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

    /** Bloquea hasta que haya un encargado libre, sin tomarlo (ver {@link ResourcePool#awaitAvailableUnit}). */
    public void awaitFreeWorker() throws InterruptedException {
        workers().awaitAvailableUnit();
    }

    /** Asigna un encargado al pedido, lo marca como en proceso y registra el instante de admisión. */
    public void assignWorker(OrderExecution execution) throws InterruptedException, OperationPreemptedException {
        workers().acquire(execution);
        execution.markAdmitted(clock.now());
        execution.resourceAcquired(ResourceType.WAREHOUSE_WORKER);
        execution.order().status(OrderStatus.PROCESSING);
    }

    /** Deshace {@link #assignWorker} (ej. si no se pudo arrancar el hilo del pedido). */
    public void unassignWorker(OrderExecution execution) {
        workers().release(execution);
        execution.resourceReleased(ResourceType.WAREHOUSE_WORKER);
        execution.order().status(OrderStatus.WAITING);
    }

    /**
     * Ejecuta las etapas del pedido hasta completarlo. Pase lo que pase (interrupción, expulsión o
     * error) libera todo lo que retiene, incluido el encargado, antes de salir.
     *
     * @return el resumen del pedido despachado, con sus tiempos de espera y de proceso
     * @throws IllegalStateException si el pedido no tiene un encargado asignado
     */
    public CompletedOrder run(OrderExecution execution) throws InterruptedException, OperationPreemptedException {
        if (!execution.holds(ResourceType.WAREHOUSE_WORKER)) {
            throw new IllegalStateException(execution.id() + " must have a worker assigned before running");
        }
        Set<ResourceType> held = EnumSet.of(ResourceType.WAREHOUSE_WORKER);
        try {
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
        return summarize(execution);
    }

    private CompletedOrder summarize(OrderExecution execution) {
        Duration completedAt = clock.now();
        Duration waited = execution.admittedAt().minus(execution.queued().arrivalTime());
        return new CompletedOrder(
                execution.id(),
                execution.order().serviceLevel(),
                execution.order().totalItems(),
                waited.isNegative() ? Duration.ZERO : waited,
                completedAt.minus(execution.admittedAt()));
    }

    private ResourcePool workers() {
        return resources.pool(ResourceType.WAREHOUSE_WORKER);
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
