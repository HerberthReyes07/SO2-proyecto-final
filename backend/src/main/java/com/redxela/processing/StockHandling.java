package com.redxela.processing;

import com.redxela.domain.CompletedOrder;
import com.redxela.inventory.Inventory;
import com.redxela.warehouse.Warehouse;

/**
 * Liga el ciclo de vida de un pedido con su reserva de stock: al despacharse, las unidades salen
 * del inventario y del almacén; si se aborta, vuelven a estar disponibles (la mercadería nunca se
 * movió del almacén, solo estaba apartada).
 *
 * <p>Orden al despachar: primero el inventario y después el almacén, de modo que las existencias
 * contables nunca superan lo que hay físicamente.
 */
public final class StockHandling implements OrderLifecycleListener {

    private final Inventory inventory;
    private final Warehouse warehouse;

    public StockHandling(Inventory inventory, Warehouse warehouse) {
        this.inventory = inventory;
        this.warehouse = warehouse;
    }

    @Override
    public void onCompleted(CompletedOrder order) {
        inventory.consume(order.orderId()).forEach(warehouse::retrieve);
    }

    @Override
    public void onAborted(String orderId) {
        inventory.release(orderId);
    }
}
