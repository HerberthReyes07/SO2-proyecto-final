package com.redxela.warehouse;

/**
 * Lo que hay en una ubicación ocupada.
 *
 * @param lotId      lote al que pertenece (una ubicación pertenece a un solo lote)
 * @param productId  producto almacenado
 * @param units      unidades guardadas
 * @param usedVolume volumen que ocupan; lo que falta hasta la capacidad es fragmentación interna
 */
public record LocationContent(String lotId, String productId, int units, int usedVolume) {
}
