package com.redxela.api;

import com.redxela.stats.SimulationStatistics;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;

/** Rutas de estadísticas: reiniciarlas para medir un experimento desde cero. */
public final class StatsRoutes implements ApiRoutes {

    private final SimulationStatistics statistics;

    public StatsRoutes(SimulationStatistics statistics) {
        this.statistics = statistics;
    }

    @Override
    public void register(Javalin app) {
        app.post("/api/stats/reset", ctx -> {
            statistics.reset();
            ctx.status(HttpStatus.NO_CONTENT);
        });
    }
}
