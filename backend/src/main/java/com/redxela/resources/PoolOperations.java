package com.redxela.resources;

import com.redxela.domain.ResourceType;
import java.util.List;

/**
 * Quién retiene y quién espera un recurso, con las propias operaciones (no solo sus ids). Es la
 * materia prima de la detección de deadlock, que necesita poder actuar sobre las operaciones.
 *
 * @param type     clase de recurso
 * @param capacity unidades totales
 * @param holders  operaciones que retienen una unidad
 * @param waiting  operaciones que esperan una unidad
 */
public record PoolOperations(ResourceType type, int capacity, List<Operation> holders, List<Operation> waiting) {

    public PoolOperations {
        holders = List.copyOf(holders);
        waiting = List.copyOf(waiting);
    }

    /** Unidades libres en este momento. */
    public int available() {
        return capacity - holders.size();
    }
}
