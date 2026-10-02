package com.redxela.stats;

/**
 * Estadísticas generales de todos los niveles juntos.
 *
 * @param completed           pedidos despachados
 * @param avgWaitMillis       espera promedio en ms
 * @param avgProcessingMillis tiempo promedio en proceso en ms
 * @param onTimeRatio         fracción de pedidos atendidos dentro del tiempo objetivo de su nivel (0 a 1)
 * @param throughputPerMinute pedidos despachados por minuto simulado desde el último reinicio
 * @param elapsedMillis       tiempo simulado transcurrido desde el último reinicio, en ms
 */
public record StatsTotals(
        long completed,
        long avgWaitMillis,
        long avgProcessingMillis,
        double onTimeRatio,
        double throughputPerMinute,
        long elapsedMillis) {
}
