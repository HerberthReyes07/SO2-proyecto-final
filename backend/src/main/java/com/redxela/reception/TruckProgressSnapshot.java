package com.redxela.reception;

import com.redxela.domain.ResourceType;
import java.util.List;

/**
 * Foto del avance de un camión en Recepción.
 *
 * @param truckId   id del camión
 * @param productId producto que trae
 * @param quantity  unidades que trae
 * @param phase     fase actual
 * @param awaiting  recurso que espera, o null si no espera ninguno
 * @param held      recursos que retiene en este momento
 * @param progress  avance de la descarga, entre 0 y 1
 * @param retries   cuántas veces fue expulsado para deshacer un deadlock y tuvo que reintentar
 */
public record TruckProgressSnapshot(
        String truckId,
        String productId,
        int quantity,
        TruckPhase phase,
        ResourceType awaiting,
        List<ResourceType> held,
        double progress,
        int retries) {

    public TruckProgressSnapshot {
        held = List.copyOf(held);
    }
}
