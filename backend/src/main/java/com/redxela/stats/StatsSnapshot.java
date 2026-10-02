package com.redxela.stats;

import java.util.List;

/**
 * Estadísticas del sistema para la interfaz. La ocupación de los recursos no va aquí: viaja en el
 * snapshot de cada pool ({@code PoolSnapshot.utilization}).
 *
 * @param totals todos los niveles juntos
 * @param levels una entrada por nivel de servicio, del 1 al 5
 */
public record StatsSnapshot(StatsTotals totals, List<LevelStats> levels) {

    public StatsSnapshot {
        levels = List.copyOf(levels);
    }
}
