package com.redxela.orders;

/**
 * Línea solicitada al crear un pedido, tal como la envía el usuario (aún sin validar).
 *
 * @param productId id del producto del catálogo
 * @param quantity  unidades solicitadas
 */
public record OrderLineRequest(String productId, int quantity) {
}
