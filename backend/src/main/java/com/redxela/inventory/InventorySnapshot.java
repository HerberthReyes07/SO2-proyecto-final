package com.redxela.inventory;

import java.util.List;

/**
 * Todo lo que la interfaz necesita saber del área de Inventario.
 *
 * @param stock  existencias de cada producto
 * @param buffer estado del buffer de recepción
 * @param worker estado del hilo que procesa los mensajes
 */
public record InventorySnapshot(List<StockSnapshot> stock, ReceptionBufferSnapshot buffer, InventoryWorkerSnapshot worker) {

    public InventorySnapshot {
        stock = List.copyOf(stock);
    }
}
