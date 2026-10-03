package com.redxela.warehouse;

import java.util.List;

/**
 * Resultado de almacenar un lote.
 *
 * @param lotId     lote
 * @param requested unidades que se querían almacenar
 * @param stored    unidades que cupieron
 * @param rejected  unidades que no cupieron por falta de ubicaciones libres (cero si todo cupo)
 * @param locations índices de las ubicaciones asignadas, en el orden en que se llenaron
 */
public record StoreResult(String lotId, int requested, int stored, int rejected, List<Integer> locations) {

    public StoreResult {
        locations = List.copyOf(locations);
    }
}
