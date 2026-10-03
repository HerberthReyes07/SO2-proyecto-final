package com.redxela.inventory;

import java.time.Duration;

/**
 * Tiempo simulado que Inventario tarda en procesar un mensaje de Recepción: un costo fijo (verificar,
 * registrar) más un costo por unidad. Es deliberadamente más lento que la llegada de camiones para
 * que se pueda observar el buffer llenándose.
 *
 * @param fixed   costo fijo por mensaje
 * @param perUnit costo por cada unidad recibida
 */
public record InventoryTimes(Duration fixed, Duration perUnit) {

    public static InventoryTimes defaults() {
        return new InventoryTimes(Duration.ofSeconds(20), Duration.ofSeconds(1));
    }

    public Duration processingTime(int quantity) {
        return fixed.plus(perUnit.multipliedBy(quantity));
    }
}
