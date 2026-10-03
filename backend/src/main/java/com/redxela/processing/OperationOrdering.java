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
 * <p>Las operaciones que no son pedidos (los camiones de recepción) van <b>antes</b> que los pedidos
 * y empatan entre sí, de modo que entre ellos desempata el orden de llegada al pool. Es una decisión
 * deliberada: la recepción es finita y desbloquea el flujo de stock, mientras que si fuera al revés,
 * bajo carga los camiones podrían no llegar a atenderse nunca. El criterio es un orden total
 * consistente, requisito para que los pools puedan ordenar sin fallar.
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
                return 1;
            }
            if (second instanceof OrderExecution) {
                return -1;
            }
            return 0;
        };
    }
}
