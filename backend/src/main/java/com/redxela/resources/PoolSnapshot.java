package com.redxela.resources;

import com.redxela.domain.ResourceType;
import java.util.List;

/**
 * Foto consistente de un pool, tomada bajo su lock. Es lo que consume la interfaz.
 *
 * @param type     clase de recurso
 * @param capacity unidades totales
 * @param holders  ids de las operaciones que retienen una unidad
 * @param waiting  ids de las operaciones en espera, en el orden en que se les concederá el recurso
 */
public record PoolSnapshot(ResourceType type, int capacity, List<String> holders, List<String> waiting) {

    public PoolSnapshot {
        holders = List.copyOf(holders);
        waiting = List.copyOf(waiting);
    }
}
