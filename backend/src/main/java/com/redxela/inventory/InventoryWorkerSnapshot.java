package com.redxela.inventory;

/**
 * Estado del hilo de Inventario que procesa los mensajes de Recepción.
 *
 * @param processing mensaje que está procesando ahora, o null si está ocioso
 * @param processed  mensajes procesados desde el inicio
 */
public record InventoryWorkerSnapshot(ReceptionMessage processing, long processed) {
}
