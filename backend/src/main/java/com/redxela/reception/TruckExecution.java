package com.redxela.reception;

import com.redxela.domain.ResourceType;
import com.redxela.resources.Operation;
import com.redxela.resources.OperationKind;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Un camión en Recepción: es la {@link Operation} que compite por recursos y a la vez el estado
 * observable de su avance.
 *
 * <p>Igual que {@code OrderExecution}, el estado es un registro inmutable en un campo
 * {@code volatile}: solo el hilo del camión lo reemplaza y el que arma los snapshots lee una foto
 * coherente sin locks.
 */
final class TruckExecution implements Operation {

    private final String id;
    private final String productId;
    private final int quantity;
    private final long sequence;
    private volatile State state = State.initial();
    // Cuántas veces fue expulsado; lo escribe solo el hilo del camión.
    private volatile int retries;

    TruckExecution(String id, String productId, int quantity, long sequence) {
        this.id = id;
        this.productId = productId;
        this.quantity = quantity;
        this.sequence = sequence;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public OperationKind kind() {
        return OperationKind.TRUCK;
    }

    String productId() {
        return productId;
    }

    int quantity() {
        return quantity;
    }

    long sequence() {
        return sequence;
    }

    // ---- Transiciones: solo las invoca el hilo del camión ----

    void beginWaiting(ResourceType resource) {
        state = state.waitingFor(resource);
    }

    void resourceAcquired(ResourceType resource) {
        state = state.acquired(resource);
    }

    void resourceReleased(ResourceType resource) {
        state = state.released(resource);
    }

    void beginManeuvering() {
        state = state.maneuvering();
    }

    /** El camión fue expulsado: ya soltó sus recursos y va a esperar antes de reintentar. */
    void beginRetrying() {
        retries++;
        state = state.retrying();
    }

    void beginUnloading(Duration work, Duration now) {
        state = state.unloading(work, now);
    }

    void beginReporting() {
        state = state.reporting();
    }

    // ---- Lectura: cualquier hilo ----

    TruckProgressSnapshot progress(Duration now) {
        State current = state; // una sola lectura volátil: todo lo que sigue es coherente
        return new TruckProgressSnapshot(id, productId, quantity, current.phase(), current.awaiting(),
                current.held(), current.progressAt(now), retries);
    }

    /**
     * Estado inmutable del avance.
     *
     * @param phase     fase actual
     * @param awaiting  recurso que se espera, o null
     * @param held      recursos retenidos, en orden global
     * @param work      duración de la descarga (cero si aún no empieza)
     * @param startedAt instante simulado en que empezó a descargar
     */
    private record State(TruckPhase phase, ResourceType awaiting, List<ResourceType> held, Duration work,
                         Duration startedAt) {

        static State initial() {
            return new State(TruckPhase.WAITING_RESOURCES, null, List.of(), Duration.ZERO, Duration.ZERO);
        }

        State waitingFor(ResourceType resource) {
            return new State(TruckPhase.WAITING_RESOURCES, resource, held, work, startedAt);
        }

        State acquired(ResourceType resource) {
            Set<ResourceType> updated = toSet(held);
            updated.add(resource);
            return new State(phase, null, List.copyOf(updated), work, startedAt);
        }

        State released(ResourceType resource) {
            Set<ResourceType> updated = toSet(held);
            updated.remove(resource);
            return new State(phase, awaiting, List.copyOf(updated), work, startedAt);
        }

        State maneuvering() {
            return new State(TruckPhase.MANEUVERING, null, held, work, startedAt);
        }

        State retrying() {
            return new State(TruckPhase.RETRYING, null, List.of(), Duration.ZERO, Duration.ZERO);
        }

        State unloading(Duration unloadWork, Duration now) {
            return new State(TruckPhase.UNLOADING, null, held, unloadWork, now);
        }

        State reporting() {
            return new State(TruckPhase.REPORTING, null, held, work, startedAt);
        }

        double progressAt(Duration now) {
            return switch (phase) {
                case WAITING_RESOURCES, MANEUVERING, RETRYING -> 0.0;
                case REPORTING -> 1.0;
                case UNLOADING -> work.isZero() ? 1.0
                        : Math.min(1.0, Math.max(0.0, (double) now.minus(startedAt).toMillis() / work.toMillis()));
            };
        }

        // EnumSet mantiene los recursos en orden global, que es el orden en que se muestran.
        private static Set<ResourceType> toSet(List<ResourceType> resources) {
            Set<ResourceType> set = EnumSet.noneOf(ResourceType.class);
            set.addAll(resources);
            return set;
        }
    }
}
