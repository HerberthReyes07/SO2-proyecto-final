package com.redxela.inventory;

import java.util.Objects;

/**
 * Mensaje con el que Recepción informa a Inventario qué mercadería llegó. Es lo único que cruza
 * entre las dos áreas: ninguna toca las estructuras internas de la otra.
 *
 * @param truckId   camión que la trajo
 * @param productId producto recibido
 * @param quantity  unidades recibidas (positivo)
 */
public record ReceptionMessage(String truckId, String productId, int quantity) {

    public ReceptionMessage {
        Objects.requireNonNull(truckId, "truckId");
        Objects.requireNonNull(productId, "productId");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }
}
