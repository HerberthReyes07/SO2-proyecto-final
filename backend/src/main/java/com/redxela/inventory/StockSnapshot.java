package com.redxela.inventory;

/**
 * Existencias de un producto.
 *
 * @param productId id del producto
 * @param available unidades libres, que pueden reservarse para nuevos pedidos
 * @param reserved  unidades apartadas para pedidos en proceso que aún no se despachan
 */
public record StockSnapshot(String productId, int available, int reserved) {
}
