package com.redxela.api;

import com.redxela.deadlock.AcquisitionPolicy;
import com.redxela.deadlock.DeadlockDetector;
import com.redxela.deadlock.PreventionMode;
import com.redxela.demo.DeadlockScenario;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import java.util.Map;

/** Rutas de deadlock: consultar, elegir la prevención, resolver a mano y lanzar el escenario de demostración. */
public final class DeadlockRoutes implements ApiRoutes {

    private static final String PATH = "/api/deadlock";

    private final DeadlockDetector detector;
    private final AcquisitionPolicy policy;
    private final DeadlockScenario scenario;

    public DeadlockRoutes(DeadlockDetector detector, AcquisitionPolicy policy, DeadlockScenario scenario) {
        this.detector = detector;
        this.policy = policy;
        this.scenario = scenario;
    }

    @Override
    public void register(Javalin app) {
        app.get(PATH, ctx -> ctx.json(detector.snapshot()));
        app.put(PATH + "/prevention", this::changePrevention);
        app.post(PATH + "/resolve", this::resolve);
        app.post(PATH + "/scenario", this::startScenario);
    }

    private void changePrevention(Context ctx) {
        ChangePreventionRequest request = ctx.bodyValidator(ChangePreventionRequest.class)
                .check(body -> body.mode() != null, "mode is required")
                .get();
        policy.setMode(request.mode());
        ctx.json(Map.of("prevention", policy.mode().name()));
    }

    private void resolve(Context ctx) {
        ResolveDeadlockRequest request = ctx.bodyValidator(ResolveDeadlockRequest.class)
                .check(body -> body.continueOperationId() != null && !body.continueOperationId().isBlank(),
                        "continueOperationId is required")
                .get();
        // Sin deadlock vigente o con una elección inválida, el detector lanza y la API responde 409 o 400.
        ctx.json(detector.resolve(request.continueOperationId()));
    }

    private void startScenario(Context ctx) {
        StartScenarioRequest request = ctx.bodyValidator(StartScenarioRequest.class).get();
        PreventionMode mode = request.mode() == null ? PreventionMode.NONE : request.mode();
        scenario.start(mode);
        ctx.status(HttpStatus.ACCEPTED).json(Map.of("prevention", mode.name()));
    }
}
