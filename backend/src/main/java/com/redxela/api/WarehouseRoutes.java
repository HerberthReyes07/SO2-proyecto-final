package com.redxela.api;

import com.redxela.warehouse.Warehouse;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.Map;

/** Rutas del almacén: consultar y cambiar la estrategia con que se eligen las ubicaciones. */
public final class WarehouseRoutes implements ApiRoutes {

    private static final String PATH = "/api/warehouse/strategy";

    private final Warehouse warehouse;

    public WarehouseRoutes(Warehouse warehouse) {
        this.warehouse = warehouse;
    }

    @Override
    public void register(Javalin app) {
        app.get(PATH, ctx -> ctx.json(current()));
        app.put(PATH, this::change);
    }

    private void change(Context ctx) {
        ChangeStrategyRequest request = ctx.bodyValidator(ChangeStrategyRequest.class)
                .check(body -> body.strategy() != null, "strategy is required")
                .get();
        warehouse.setStrategy(request.strategy());
        ctx.json(current());
    }

    private Map<String, String> current() {
        return Map.of("strategy", warehouse.strategy().name());
    }
}
