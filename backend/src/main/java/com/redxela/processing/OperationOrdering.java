package com.redxela.processing;

import com.redxela.domain.QueuedOrder;
import com.redxela.resources.Operation;
import com.redxela.scheduling.SchedulingPolicy;
import java.util.Comparator;

/**
 * Adapta una {@link SchedulingPolicy} (que ordena pedidos) al criterio que usan los pools de
 * recursos (que ordenan operaciones). Así la misma política decide quién entra primero a una
 * estación, no solo quién sale primero de la lista de espera.
 *
 * <p>Las operaciones que no son pedidos van después de los pedidos y empatan entre sí. El criterio
 * es un orden total consistente, requisito para que los pools puedan ordenar sin fallar.
 */
public final class OperationOrdering {

    private OperationOrdering() {
    }

    public static Comparator<Operation> of(SchedulingPolicy policy) {
        Comparator<QueuedOrder> byPolicy = policy.comparator();
        return (first, second) -> {
            if (first instanceof OrderExecution a && second instanceof OrderExecution b) {
                return byPolicy.compare(a.queued(), b.queued());
            }
            if (first instanceof OrderExecution) {
                return -1;
            }
            if (second instanceof OrderExecution) {
                return 1;
            }
            return 0;
        };
    }
}
