package com.redxela.api;

/**
 * Cuerpo de {@code POST /api/benchmark}. Todo es opcional: lo que falte toma un valor por defecto.
 *
 * @param orders          pedidos de la carga de prueba
 * @param ordersPerMinute intensidad de llegada, en pedidos por minuto simulado
 * @param seed            semilla; ausente genera una al azar (se devuelve en el estado para poder repetir la prueba)
 */
public record StartBenchmarkRequest(Integer orders, Double ordersPerMinute, Long seed) {
}
