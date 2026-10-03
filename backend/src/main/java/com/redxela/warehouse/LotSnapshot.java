package com.redxela.warehouse;

import java.util.List;

/**
 * Un lote y dónde está guardado: su "tabla de páginas".
 *
 * @param lotId     identificador del lote
 * @param productId producto
 * @param units     unidades que quedan del lote
 * @param locations índices de las ubicaciones que ocupa, de menor a mayor
 * @param runs      cantidad de tramos contiguos en que está repartido (1 = todo junto; más = disperso)
 */
public record LotSnapshot(String lotId, String productId, int units, List<Integer> locations, int runs) {

    public LotSnapshot {
        locations = List.copyOf(locations);
    }
}
