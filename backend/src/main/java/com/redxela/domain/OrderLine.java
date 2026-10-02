package com.redxela.domain;

import java.util.Objects;

/**
 * Línea de un pedido: un producto y la cantidad de unidades solicitadas.
 *
 * @param product  producto solicitado
 * @param quantity unidades solicitadas (mayor que cero)
 */
public record OrderLine(Product product, int quantity) {

    public OrderLine {
        Objects.requireNonNull(product, "product");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }
}
