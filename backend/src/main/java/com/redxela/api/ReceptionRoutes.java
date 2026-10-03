package com.redxela.api;

import com.redxela.reception.ReceptionService;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

/** Rutas de Recepción: registrar la llegada de mercadería. Lo que sigue (inventario, ubicaciones) es automático. */
public final class ReceptionRoutes implements ApiRoutes {

    private static final int DEFAULT_TRUCKS = 1;

    private final ReceptionService reception;

    public ReceptionRoutes(ReceptionService reception) {
        this.reception = reception;
    }

    @Override
    public void register(Javalin app) {
        app.post("/api/receptions", this::registerArrival);
    }

    private void registerArrival(Context ctx) {
        RegisterArrivalRequest request = ctx.bodyValidator(RegisterArrivalRequest.class).get();
        int trucks = request.trucks() == null ? DEFAULT_TRUCKS : request.trucks();
        // 202 y no 201: el camión queda en Recepción y el resto del proceso ocurre después, a su ritmo.
        ctx.status(HttpStatus.ACCEPTED)
                .json(new ArrivalConfirmation(reception.registerArrival(request.productId(), request.quantity(), trucks)));
    }
}
