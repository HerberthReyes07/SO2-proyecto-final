package com.redxela.domain;

import java.util.Objects;

/**
 * Producto del catálogo (predefinido, no se crea desde la interfaz).
 *
 * @param id         identificador único (SKU)
 * @param name       nombre legible
 * @param type       tipo de mercancía, que determina los recursos que necesita
 * @param unitVolume volumen que ocupa una unidad, en unidades de volumen del almacén
 */
public record Product(String id, String name, MerchandiseType type, int unitVolume) {

    public Product {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        if (unitVolume <= 0) {
            throw new IllegalArgumentException("unitVolume must be positive");
        }
    }
}
