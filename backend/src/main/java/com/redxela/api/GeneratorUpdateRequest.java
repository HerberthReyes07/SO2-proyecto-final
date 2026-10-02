package com.redxela.api;

/**
 * Cuerpo de {@code PUT /api/generator}. Es una actualización parcial: los campos ausentes (null) no cambian.
 *
 * @param enabled         {@code true} para empezar a generar pedidos, {@code false} para detenerse
 * @param ordersPerMinute intensidad, en pedidos por minuto simulado
 * @param seed            semilla de la secuencia de pedidos; una semilla nueva la reinicia desde el principio
 */
public record GeneratorUpdateRequest(Boolean enabled, Double ordersPerMinute, Long seed) {
}
