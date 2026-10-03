package com.redxela.inventory;

/**
 * Faltante de un producto para poder atender un pedido.
 *
 * @param productId    id del producto
 * @param missingUnits unidades que faltan (siempre positivo)
 */
public record MissingStock(String productId, int missingUnits) {
}
