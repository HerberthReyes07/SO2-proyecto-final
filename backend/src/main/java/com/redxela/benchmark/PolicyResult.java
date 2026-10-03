package com.redxela.benchmark;

import com.redxela.scheduling.SchedulingPolicy;
import com.redxela.stats.StatsSnapshot;

/**
 * Cómo le fue a una política con la carga del experimento. Todos los tiempos son simulados.
 *
 * @param policy            la política
 * @param stats             estadísticas de los pedidos despachados (totales y por nivel de servicio)
 * @param durationMillis    tiempo que tardó en despachar toda la traza, desde la primera llegada, en ms
 * @param workerUtilization ocupación promedio de los encargados de bodega durante la corrida, entre 0 y 1
 */
public record PolicyResult(SchedulingPolicy policy, StatsSnapshot stats, long durationMillis, double workerUtilization) {
}
