package com.redxela.api;

import com.redxela.processing.OrderScheduler;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.Map;

/** Rutas para consultar y cambiar la política de atención. */
public final class SchedulingRoutes implements ApiRoutes {

    private static final String PATH = "/api/scheduling/policy";

    private final OrderScheduler scheduler;

    public SchedulingRoutes(OrderScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public void register(Javalin app) {
        app.get(PATH, ctx -> ctx.json(currentPolicy()));
        app.put(PATH, this::change);
    }

    private void change(Context ctx) {
        ChangePolicyRequest request = ctx.bodyValidator(ChangePolicyRequest.class)
                .check(body -> body.policy() != null, "policy is required")
                .get();
        scheduler.setPolicy(request.policy());
        ctx.json(currentPolicy());
    }

    private Map<String, String> currentPolicy() {
        return Map.of("policy", scheduler.policy().name());
    }
}
