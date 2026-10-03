package com.redxela.deadlock;

import com.redxela.domain.ResourceType;
import com.redxela.resources.Operation;
import com.redxela.resources.PoolOperations;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Detecta deadlocks a partir de quién retiene y quién espera cada recurso. Es una función pura:
 * recibe datos y devuelve un resultado, sin hilos ni estado, por lo que se prueba con total precisión.
 *
 * <h3>Por qué no basta un grafo de espera</h3>
 * Los recursos tienen varias unidades (2 áreas de carga, 6 estaciones...). Con una sola unidad por
 * recurso, un ciclo en el grafo de espera equivale a un deadlock; con varias, un ciclo es solo una
 * condición <i>necesaria</i>: otra unidad libre podría romperlo. Por eso se usa el algoritmo de
 * detección para recursos de varias instancias (Silberschatz, sección 7.6.2), con tres estructuras:
 * <ul>
 *   <li><b>Asignación:</b> qué retiene cada operación;</li>
 *   <li><b>Solicitud:</b> qué espera cada operación;</li>
 *   <li><b>Disponible:</b> cuántas unidades libres hay de cada recurso.</li>
 * </ul>
 *
 * <h3>El algoritmo</h3>
 * Se parte de {@code trabajo = Disponible}. Se marca como "puede terminar" a toda operación que no
 * retiene nada, o cuya solicitud cabe en {@code trabajo}; al terminar, devuelve lo que retenía y
 * eso se suma a {@code trabajo}. Se repite hasta que no haya más cambios. Las operaciones que
 * <b>no</b> pueden terminar están en deadlock.
 *
 * <p>Una operación puede esperar a lo sumo un recurso a la vez, porque pide sus recursos de a uno.
 */
public final class DeadlockAnalyzer {

    /** Una operación del análisis con lo que retiene y lo que espera. */
    record Participant(Operation operation, Set<ResourceType> held, ResourceType waitingFor) {

        String id() {
            return operation.id();
        }

        boolean waitsFor(Participant other) {
            return waitingFor != null && other.held.contains(waitingFor);
        }
    }

    /** Resultado: las operaciones bloqueadas y el par en conflicto. */
    record Analysis(List<Participant> deadlocked, List<ResourceType> resources, Participant waiter, Participant holder) {

        /** Identifica este deadlock: las mismas operaciones bloqueadas dan la misma clave. */
        String key() {
            return deadlocked.stream().map(Participant::id).sorted().reduce((a, b) -> a + "," + b).orElse("");
        }
    }

    private DeadlockAnalyzer() {
    }

    /** Devuelve el deadlock presente en {@code pools}, o vacío si no hay ninguno. */
    static Optional<Analysis> analyze(List<PoolOperations> pools) {
        Map<Operation, MutableState> states = new LinkedHashMap<>();
        EnumMap<ResourceType, Integer> work = new EnumMap<>(ResourceType.class);
        for (PoolOperations pool : pools) {
            work.put(pool.type(), pool.available());
            for (Operation holder : pool.holders()) {
                states.computeIfAbsent(holder, key -> new MutableState()).held.add(pool.type());
            }
            for (Operation waiter : pool.waiting()) {
                states.computeIfAbsent(waiter, key -> new MutableState()).waitingFor = pool.type();
            }
        }

        Set<Operation> canFinish = finishWhatCanFinish(states, work);

        List<Participant> deadlocked = states.entrySet().stream()
                .filter(entry -> !canFinish.contains(entry.getKey()))
                .map(entry -> new Participant(entry.getKey(), EnumSet.copyOf(entry.getValue().held), entry.getValue().waitingFor))
                .sorted(Comparator.comparing(Participant::id))
                .toList();
        if (deadlocked.isEmpty()) {
            return Optional.empty();
        }
        Participant[] conflict = findConflict(deadlocked);
        return Optional.of(new Analysis(deadlocked, involvedResources(deadlocked), conflict[0], conflict[1]));
    }

    /** El núcleo del algoritmo: va "terminando" operaciones y recuperando sus recursos. */
    private static Set<Operation> finishWhatCanFinish(Map<Operation, MutableState> states, EnumMap<ResourceType, Integer> work) {
        Set<Operation> finished = new HashSet<>();
        states.forEach((operation, state) -> {
            if (state.held.isEmpty()) {
                finished.add(operation); // no retiene nada: no puede formar parte de un ciclo
            }
        });
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Map.Entry<Operation, MutableState> entry : states.entrySet()) {
                MutableState state = entry.getValue();
                boolean requestFits = state.waitingFor == null || work.getOrDefault(state.waitingFor, 0) >= 1;
                if (!finished.contains(entry.getKey()) && requestFits) {
                    state.held.forEach(type -> work.merge(type, 1, Integer::sum)); // al terminar devuelve lo que retenía
                    finished.add(entry.getKey());
                    changed = true;
                }
            }
        }
        return finished;
    }

    /**
     * Elige el par de operaciones en conflicto: primero uno que se espere mutuamente (la forma clásica
     * del deadlock); si no existe, el primer tramo del ciclo más corto. Siempre hay al menos un ciclo
     * entre las bloqueadas: cada una espera un recurso cuyas unidades retienen, todas, operaciones
     * también bloqueadas, así que cada una tiene al menos una arista de salida.
     */
    private static Participant[] findConflict(List<Participant> deadlocked) {
        for (int i = 0; i < deadlocked.size(); i++) {
            for (int j = i + 1; j < deadlocked.size(); j++) {
                Participant a = deadlocked.get(i);
                Participant b = deadlocked.get(j);
                if (a.waitsFor(b) && b.waitsFor(a)) {
                    return new Participant[] {a, b};
                }
            }
        }
        List<Participant> shortest = shortestCycle(deadlocked);
        return new Participant[] {shortest.get(0), shortest.get(1)};
    }

    /** Ciclo más corto del grafo "A espera lo que retiene B", por búsqueda en anchura desde cada operación. */
    private static List<Participant> shortestCycle(List<Participant> deadlocked) {
        List<Participant> best = null;
        for (Participant start : deadlocked) {
            Map<Participant, Participant> cameFrom = new LinkedHashMap<>();
            List<Participant> queue = new ArrayList<>(List.of(start));
            Participant closing = null;
            for (int head = 0; head < queue.size() && closing == null; head++) {
                Participant current = queue.get(head);
                for (Participant next : deadlocked) {
                    if (current.waitsFor(next)) {
                        if (next == start) {
                            closing = current;
                            break;
                        }
                        if (!cameFrom.containsKey(next) && next != start) {
                            cameFrom.put(next, current);
                            queue.add(next);
                        }
                    }
                }
            }
            if (closing != null) {
                List<Participant> cycle = new ArrayList<>();
                for (Participant node = closing; node != null; node = cameFrom.get(node)) {
                    cycle.add(0, node);
                }
                if (best == null || cycle.size() < best.size()) {
                    best = cycle;
                }
            }
        }
        if (best == null || best.size() < 2) {
            throw new IllegalStateException("A deadlock without a cycle among its operations is impossible");
        }
        return best;
    }

    /**
     * Los recursos disputados: los que las operaciones bloqueadas esperan. Un recurso que alguna retiene pero
     * que ninguna espera (por ejemplo el encargado de bodega, que cada pedido retiene siempre) no forma parte
     * del conflicto y solo ensuciaría la explicación.
     */
    private static List<ResourceType> involvedResources(List<Participant> deadlocked) {
        Set<ResourceType> involved = EnumSet.noneOf(ResourceType.class);
        for (Participant participant : deadlocked) {
            if (participant.waitingFor != null) {
                involved.add(participant.waitingFor);
            }
        }
        return List.copyOf(involved); // EnumSet itera en orden global de recursos
    }

    /** Lo que retiene y espera una operación mientras se arma el análisis. */
    private static final class MutableState {
        private final Set<ResourceType> held = EnumSet.noneOf(ResourceType.class);
        private ResourceType waitingFor;
    }
}
