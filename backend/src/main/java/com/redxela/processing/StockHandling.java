package com.redxela.processing;

import com.redxela.domain.CompletedOrder;
import com.redxela.inventory.Inventory;

/**
 * Liga el ciclo de vida de un pedido con su reserva de stock: al despacharse, las unidades salen
 * del inventario; si se aborta, vuelven a estar disponibles.
 */
public final class StockHandling implements OrderLifecycleListener {

    private final Inventory inventory;

    public StockHandling(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public void onCompleted(CompletedOrder order) {
        inventory.consume(order.orderId());
    }

    @Override
    public void onAborted(String orderId) {
        inventory.release(orderId);
    }
}
