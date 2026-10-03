package com.redxela.deadlock;

import com.redxela.domain.ResourceType;
import com.redxela.resources.OperationKind;
import java.util.List;

/**
 * Una operación bloqueada en un deadlock.
 *
 * @param id         identificador (ej. "ORD-0007", "TRK-0002")
 * @param kind       si es un pedido o un camión
 * @param held       recursos que retiene, en orden global
 * @param waitingFor recurso que espera y no recibirá mientras dure el deadlock
 */
public record DeadlockedOperation(String id, OperationKind kind, List<ResourceType> held, ResourceType waitingFor) {

    public DeadlockedOperation {
        held = List.copyOf(held);
    }
}
