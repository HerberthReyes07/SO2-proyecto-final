package com.redxela.deadlock;

import com.redxela.domain.ResourceType;
import java.util.List;

/**
 * Un deadlock confirmado.
 *
 * @param id               identificador (ej. "DL-0001"); se mantiene mientras el mismo deadlock persiste
 * @param detectedAtMillis tiempo simulado en que se confirmó, en ms
 * @param operations       todas las operaciones bloqueadas
 * @param resources        recursos disputados (los que las operaciones bloqueadas esperan), en orden global
 * @param conflict         el par de operaciones entre las que se elige cuál continúa
 */
public record DeadlockReport(
        String id,
        long detectedAtMillis,
        List<DeadlockedOperation> operations,
        List<ResourceType> resources,
        ConflictPair conflict) {

    public DeadlockReport {
        operations = List.copyOf(operations);
        resources = List.copyOf(resources);
    }
}
