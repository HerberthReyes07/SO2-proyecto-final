package com.redxela.api;

import com.redxela.sim.SimClock;
import io.javalin.Javalin;
import io.javalin.http.Context;

/** Rutas para consultar y controlar el reloj simulado. */
public final class ClockRoutes implements ApiRoutes {

    private static final String PATH = "/api/clock";

    private final SimClock clock;

    public ClockRoutes(SimClock clock) {
        this.clock = clock;
    }

    @Override
    public void register(Javalin app) {
        app.get(PATH, ctx -> ctx.json(clock.snapshot()));
        app.patch(PATH, this::update);
    }

    private void update(Context ctx) {
        ClockUpdateRequest request = ctx.bodyValidator(ClockUpdateRequest.class).get();

        // Primero la velocidad: si es inválida lanza antes de tocar la pausa y no queda nada a medias.
        if (request.speed() != null) {
            clock.setSpeed(request.speed());
        }
        if (request.paused() != null) {
            if (request.paused()) {
                clock.pause();
            } else {
                clock.resume();
            }
        }
        ctx.json(clock.snapshot());
    }
}
