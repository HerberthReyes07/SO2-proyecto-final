package com.redxela.processing;

import com.redxela.domain.Order;
import com.redxela.domain.ResourceType;
import com.redxela.domain.Stage;
import com.redxela.resources.Operation;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Ejecución de un pedido: es la {@link Operation} que compite por recursos y a la vez el estado
 * observable de su avance.
 *
 * <h3>Concurrencia</h3>
 * El estado vive en un único registro inmutable ({@link State}) guardado en un campo
 * {@code volatile}. Solo el hilo del pedido lo reemplaza, y cualquier hilo lector (el que arma los
 * snapshots) obtiene siempre una foto coherente sin necesidad de locks. Un lector nunca ve, por
 * ejemplo, un recurso en {@code held} y a la vez en {@code awaiting}.
 */
public final class OrderExecution implements Operation {

    private final Order order;
    private final ExecutionPlan plan;
    private final long sequence;
    private volatile State state = State.initial();

    /**
     * @param sequence número de llegada; permite mostrar los pedidos en orden de creación
     */
    public OrderExecution(Order order, ExecutionPlan plan, long sequence) {
        this.order = order;
        this.plan = plan;
        this.sequence = sequence;
    }

    @Override
    public String id() {
        return order.id();
    }

    public Order order() {
        return order;
    }

    public ExecutionPlan plan() {
        return plan;
    }

    public long sequence() {
        return sequence;
    }

    // ---- Transiciones: solo las invoca el hilo del pedido ----

    public void beginWaiting(Stage stage, ResourceType resource) {
        state = state.waitingFor(stage, resource);
    }

    public void resourceAcquired(ResourceType resource) {
        state = state.acquired(resource);
    }

    public void resourceReleased(ResourceType resource) {
        state = state.released(resource);
    }

    public void beginWork(Stage stage, Duration stageWork, Duration now) {
        state = state.working(stage, stageWork, now);
    }

    public void finishStageWork() {
        state = state.stageFinished();
    }

    // ---- Lectura: cualquier hilo ----

    /** Foto del avance en el instante simulado {@code now}. */
    public OrderProgressSnapshot progress(Duration now) {
        State current = state; // una sola lectura volátil: todo lo que sigue es coherente
        Duration total = plan.totalWork();
        Duration done = current.workDone().plus(current.workInProgress(now));
        double fraction = total.isZero() ? 1.0 : Math.min(1.0, (double) done.toMillis() / total.toMillis());
        Duration remaining = total.minus(done).isNegative() ? Duration.ZERO : total.minus(done);

        return new OrderProgressSnapshot(
                order.id(),
                order.customer().name(),
                order.serviceLevel(),
                order.totalItems(),
                order.status(),
                current.stage(),
                current.awaiting(),
                current.held(),
                fraction,
                remaining.toMillis());
    }

    /**
     * Estado inmutable del avance.
     *
     * @param stage          etapa actual
     * @param awaiting       recurso que se espera, o null
     * @param held           recursos retenidos, en orden global
     * @param workDone       trabajo de las etapas ya terminadas
     * @param stageWork      trabajo de la etapa en curso (cero si no está trabajando)
     * @param stageStartedAt instante simulado en que empezó a trabajar en la etapa actual
     */
    private record State(
            Stage stage,
            ResourceType awaiting,
            List<ResourceType> held,
            Duration workDone,
            Duration stageWork,
            Duration stageStartedAt) {

        static State initial() {
            return new State(Stage.PICKING, null, List.of(), Duration.ZERO, Duration.ZERO, Duration.ZERO);
        }

        State waitingFor(Stage newStage, ResourceType resource) {
            return new State(newStage, resource, held, workDone, Duration.ZERO, Duration.ZERO);
        }

        State acquired(ResourceType resource) {
            Set<ResourceType> updated = toSet(held);
            updated.add(resource);
            return new State(stage, null, List.copyOf(updated), workDone, stageWork, stageStartedAt);
        }

        State released(ResourceType resource) {
            Set<ResourceType> updated = toSet(held);
            updated.remove(resource);
            return new State(stage, awaiting, List.copyOf(updated), workDone, stageWork, stageStartedAt);
        }

        State working(Stage newStage, Duration work, Duration now) {
            return new State(newStage, null, held, workDone, work, now);
        }

        State stageFinished() {
            return new State(stage, awaiting, held, workDone.plus(stageWork), Duration.ZERO, Duration.ZERO);
        }

        /** Trabajo avanzado dentro de la etapa en curso; nunca excede el total de la etapa. */
        Duration workInProgress(Duration now) {
            if (stageWork.isZero()) {
                return Duration.ZERO;
            }
            Duration elapsed = now.minus(stageStartedAt);
            if (elapsed.isNegative()) {
                return Duration.ZERO;
            }
            return elapsed.compareTo(stageWork) > 0 ? stageWork : elapsed;
        }

        // EnumSet mantiene los recursos en orden de rank, que es el orden en que se muestran.
        private static Set<ResourceType> toSet(List<ResourceType> resources) {
            Set<ResourceType> set = EnumSet.noneOf(ResourceType.class);
            set.addAll(resources);
            return set;
        }
    }
}
