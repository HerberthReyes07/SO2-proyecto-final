package com.redxela.warehouse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Foto consistente del almacén, tomada bajo su lock.
 *
 * @param layout          geometría del almacén
 * @param strategy        estrategia vigente para elegir ubicaciones
 * @param usedLocations   ubicaciones ocupadas
 * @param totalLocations  ubicaciones totales
 * @param wastedVolume    volumen perdido por fragmentación interna: capacidad de las ubicaciones ocupadas menos lo que realmente guardan
 * @param rejectedUnits   unidades rechazadas desde el inicio por no haber ubicaciones libres
 * @param locations       contenido de cada ubicación según su índice; {@code null} si está libre
 * @param lots            lotes con sus ubicaciones, en orden de llegada
 */
public record WarehouseSnapshot(
        WarehouseLayout layout,
        PlacementStrategy strategy,
        int usedLocations,
        int totalLocations,
        long wastedVolume,
        long rejectedUnits,
        List<LocationContent> locations,
        List<LotSnapshot> lots) {

    public WarehouseSnapshot {
        // List.copyOf no admite nulos, y aquí un nulo significa "ubicación libre"; se hace una copia inmutable aparte.
        locations = Collections.unmodifiableList(new ArrayList<>(locations));
        lots = List.copyOf(lots);
    }
}
