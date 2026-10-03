package com.redxela.api;

import com.redxela.benchmark.BenchmarkParams;
import com.redxela.benchmark.BenchmarkRunner;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import java.util.concurrent.ThreadLocalRandom;

/** Rutas del comparador de políticas: lanzar una comparación, consultarla y cancelarla. */
public final class BenchmarkRoutes implements ApiRoutes {

    private static final String PATH = "/api/benchmark";
    static final int DEFAULT_ORDERS = 120;
    static final double DEFAULT_ORDERS_PER_MINUTE = 3.0;

    private final BenchmarkRunner runner;

    public BenchmarkRoutes(BenchmarkRunner runner) {
        this.runner = runner;
    }

    @Override
    public void register(Javalin app) {
        app.get(PATH, ctx -> ctx.json(runner.snapshot()));
        app.post(PATH, this::start);
        app.delete(PATH, this::cancel);
    }

    private void start(Context ctx) {
        StartBenchmarkRequest request = ctx.bodyValidator(StartBenchmarkRequest.class).get();
        BenchmarkParams params = new BenchmarkParams(
                request.orders() == null ? DEFAULT_ORDERS : request.orders(),
                request.ordersPerMinute() == null ? DEFAULT_ORDERS_PER_MINUTE : request.ordersPerMinute(),
                request.seed() == null ? ThreadLocalRandom.current().nextLong(Integer.MAX_VALUE) : request.seed());
        // Parámetros fuera de rango lanzan IllegalArgumentException (400); una comparación en marcha, StateConflictException (409).
        runner.start(params);
        ctx.status(HttpStatus.ACCEPTED).json(runner.snapshot());
    }

    private void cancel(Context ctx) {
        // Idempotente: cancelar sin nada en marcha no es un error, el resultado buscado (que no corra) ya se cumple.
        runner.cancel();
        ctx.status(HttpStatus.NO_CONTENT);
    }
}
