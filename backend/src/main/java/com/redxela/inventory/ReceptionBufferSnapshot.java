package com.redxela.inventory;

/**
 * Estado del buffer entre Recepción e Inventario.
 *
 * @param capacity         mensajes que caben
 * @param size             mensajes esperando a que Inventario los procese
 * @param blockedProducers productores esperando espacio (contrapresión: Recepción va más rápido que Inventario)
 * @param accepted         mensajes aceptados desde el inicio
 * @param delivered        mensajes que Inventario ya retiró
 */
public record ReceptionBufferSnapshot(int capacity, int size, int blockedProducers, long accepted, long delivered) {
}
