package com.redxela.domain;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pedido de un cliente.
 *
 * <p>Es inmutable salvo por su {@link OrderStatus}, que cambia desde varios hilos (despachador,
 * operación del pedido, inventario) y por eso se declara {@code volatile}: basta con visibilidad,
 * porque cada transición la escribe un único hilo a la vez.
 */
public final class Order {

    private final String id;
    private final Customer customer;
    private final List<OrderLine> lines;
    private volatile OrderStatus status = OrderStatus.WAITING;

    public Order(String id, Customer customer, List<OrderLine> lines) {
        this.id = Objects.requireNonNull(id, "id");
        this.customer = Objects.requireNonNull(customer, "customer");
        Objects.requireNonNull(lines, "lines");
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("An order must have at least one line");
        }
        this.lines = List.copyOf(lines);
    }

    public String id() {
        return id;
    }

    public Customer customer() {
        return customer;
    }

    public ServiceLevel serviceLevel() {
        return customer.level();
    }

    public List<OrderLine> lines() {
        return lines;
    }

    /** Total de artículos del pedido: es la estimación base del trabajo a realizar. */
    public int totalItems() {
        return lines.stream().mapToInt(OrderLine::quantity).sum();
    }

    /** Tipos de mercancía distintos presentes en el pedido. */
    public Set<MerchandiseType> merchandiseTypes() {
        return lines.stream()
                .map(line -> line.product().type())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Recursos que necesita el pedido, en orden global de adquisición. */
    public List<ResourceType> requiredResources() {
        return ResourceRecipe.forTypes(merchandiseTypes());
    }

    public OrderStatus status() {
        return status;
    }

    public void status(OrderStatus newStatus) {
        this.status = Objects.requireNonNull(newStatus, "status");
    }
}
