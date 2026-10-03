package com.redxela.benchmark;

import com.redxela.generator.OrderGenerator;

/**
 * Qué carga se usa para comparar las políticas.
 *
 * @param orders          cantidad de pedidos de la traza
 * @param ordersPerMinute intensidad de llegada, en pedidos por minuto simulado
 * @param seed            semilla de la traza: la misma semilla produce siempre los mismos pedidos y llegadas
 */
public record BenchmarkParams(int orders, double ordersPerMinute, long seed) {

    public static final int MIN_ORDERS = 10;
    public static final int MAX_ORDERS = 500;

    /**
     * @throws IllegalArgumentException si la cantidad de pedidos o la intensidad están fuera de rango
     */
    public BenchmarkParams {
        if (orders < MIN_ORDERS || orders > MAX_ORDERS) {
            throw new IllegalArgumentException("orders must be between " + MIN_ORDERS + " and " + MAX_ORDERS + " but was " + orders);
        }
        boolean validRate = Double.isFinite(ordersPerMinute)
                && ordersPerMinute >= OrderGenerator.MIN_ORDERS_PER_MINUTE
                && ordersPerMinute <= OrderGenerator.MAX_ORDERS_PER_MINUTE;
        if (!validRate) {
            throw new IllegalArgumentException("ordersPerMinute must be between " + OrderGenerator.MIN_ORDERS_PER_MINUTE
                    + " and " + OrderGenerator.MAX_ORDERS_PER_MINUTE + " but was " + ordersPerMinute);
        }
    }
}
