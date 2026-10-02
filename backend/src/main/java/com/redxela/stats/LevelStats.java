package com.redxela.stats;

import com.redxela.domain.ServiceLevel;

/**
 * Estadísticas de los pedidos despachados de un nivel de servicio. Los tiempos son simulados.
 *
 * @param level               nivel de servicio
 * @param completed           pedidos despachados
 * @param avgWaitMillis       espera promedio en la lista de espera, en ms (0 si no hay pedidos)
 * @param maxWaitMillis       mayor espera registrada, en ms; revela inanición
 * @param avgProcessingMillis tiempo promedio en proceso, en ms
 * @param onTimeRatio         fracción de pedidos que empezaron a atenderse dentro del tiempo objetivo del nivel (0 a 1)
 */
public record LevelStats(
        ServiceLevel level,
        long completed,
        long avgWaitMillis,
        long maxWaitMillis,
        long avgProcessingMillis,
        double onTimeRatio) {
}
