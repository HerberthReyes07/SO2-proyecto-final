package com.redxela.api;

/**
 * Cuerpo de {@code POST /api/receptions}: llegada de mercadería.
 *
 * @param productId producto del catálogo
 * @param quantity  unidades que trae cada camión
 * @param trucks    cantidad de camiones iguales; ausente (null) equivale a uno. Varios camiones permiten
 *                  provocar una ráfaga y ver cómo se llena el buffer de Inventario
 */
public record RegisterArrivalRequest(String productId, int quantity, Integer trucks) {
}
