package com.redxela.domain;

import java.util.Objects;

/**
 * Cuenta de cliente. El nivel de servicio se elige al crear la cuenta y se aplica a todos sus pedidos.
 *
 * @param id    identificador único
 * @param name  nombre del cliente
 * @param level nivel de servicio contratado
 */
public record Customer(String id, String name, ServiceLevel level) {

    public Customer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(level, "level");
    }
}
