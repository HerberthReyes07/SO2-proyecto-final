package com.redxela.api;

import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import java.util.Map;

/** Rutas que afectan a la simulación completa; por ahora, reiniciarla. */
public final class SimulationRoutes implements ApiRoutes {

    private static final String RESET_PATH = "/api/simulation/reset";

    private final Runnable resetRequest;

    /**
     * @param resetRequest pide el reinicio; debe retornar sin esperar a que termine, porque reiniciar detiene
     *                     este mismo servidor y la respuesta de esta petición tiene que alcanzar a salir antes
     */
    public SimulationRoutes(Runnable resetRequest) {
        this.resetRequest = resetRequest;
    }

    @Override
    public void register(Javalin app) {
        app.post(RESET_PATH, ctx -> {
            resetRequest.run();
            // 202 y no 200: el reinicio es asíncrono; el cliente lo nota cuando el WebSocket se reconecta.
            ctx.status(HttpStatus.ACCEPTED).json(Map.of("status", "resetting"));
        });
    }
}
