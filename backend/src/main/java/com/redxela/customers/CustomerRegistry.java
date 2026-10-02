package com.redxela.customers;

import com.redxela.domain.Customer;
import com.redxela.domain.ServiceLevel;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cuentas de cliente, en memoria.
 *
 * <p>Es segura entre hilos sin locks propios: el mapa es concurrente, los {@link Customer} son
 * inmutables y los ids salen de un contador atómico.
 */
public final class CustomerRegistry {

    static final int MAX_NAME_LENGTH = 60;

    private final Map<String, Customer> customersById = new ConcurrentHashMap<>();
    private final AtomicInteger nextNumber = new AtomicInteger();

    /** Registro con un cliente de ejemplo por cada nivel de servicio, para poder probar de inmediato. */
    public static CustomerRegistry withSampleCustomers() {
        CustomerRegistry registry = new CustomerRegistry();
        registry.create("Aurora Tech", ServiceLevel.EXPRESS);
        registry.create("Boticas del Sur", ServiceLevel.PRIORITY);
        registry.create("Casa Verde", ServiceLevel.STANDARD);
        registry.create("Distribuidora Lagos", ServiceLevel.SCHEDULED);
        registry.create("Econo Mart", ServiceLevel.ECONOMY);
        return registry;
    }

    /**
     * Crea una cuenta nueva con el nivel de servicio elegido.
     *
     * @throws IllegalArgumentException si el nombre está vacío o es demasiado largo
     */
    public Customer create(String name, ServiceLevel level) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Customer name must not be blank");
        }
        if (level == null) {
            throw new IllegalArgumentException("Customer service level is required");
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Customer name must have at most " + MAX_NAME_LENGTH + " characters");
        }
        Customer customer = new Customer(String.format("CUS-%03d", nextNumber.incrementAndGet()), trimmed, level);
        customersById.put(customer.id(), customer);
        return customer;
    }

    public Optional<Customer> findById(String id) {
        return Optional.ofNullable(customersById.get(id));
    }

    /** Todos los clientes, en orden de creación. */
    public List<Customer> all() {
        return customersById.values().stream().sorted(Comparator.comparing(Customer::id)).toList();
    }
}
