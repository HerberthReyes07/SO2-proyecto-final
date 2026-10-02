package com.redxela.api;

import com.redxela.generator.OrderGenerator;
import io.javalin.Javalin;
import io.javalin.http.Context;

/** Rutas para consultar y controlar el generador automático de pedidos. */
public final class GeneratorRoutes implements ApiRoutes {

    private static final String PATH = "/api/generator";

    private final OrderGenerator generator;

    public GeneratorRoutes(OrderGenerator generator) {
        this.generator = generator;
    }

    @Override
    public void register(Javalin app) {
        app.get(PATH, ctx -> ctx.json(generator.snapshot()));
        app.put(PATH, this::update);
    }

    private void update(Context ctx) {
        GeneratorUpdateRequest request = ctx.bodyValidator(GeneratorUpdateRequest.class).get();
        // Si algún valor no es válido, configure lanza antes de modificar nada y el cliente recibe 400.
        ctx.json(generator.configure(request.enabled(), request.ordersPerMinute(), request.seed()));
    }
}
