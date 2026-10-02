package com.redxela.generator;

/**
 * Estado del generador para la interfaz.
 *
 * @param enabled           si está generando pedidos
 * @param ordersPerMinute   intensidad: pedidos por minuto simulado
 * @param seed              semilla vigente; anotarla permite repetir la misma secuencia de pedidos
 * @param generated         pedidos generados desde que arrancó la aplicación
 */
public record GeneratorSnapshot(boolean enabled, double ordersPerMinute, long seed, long generated) {
}
