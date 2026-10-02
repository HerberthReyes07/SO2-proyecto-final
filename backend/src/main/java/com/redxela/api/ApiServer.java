package com.redxela.api;

import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.http.staticfiles.Location;
import java.util.List;
import java.util.Map;

/**
 * Servidor HTTP: expone la API REST, el WebSocket de estado y los archivos estáticos del frontend.
 *
 * <p>Es la única clase del backend que conoce Javalin; el núcleo de la simulación es Java plano.
 */
public final class ApiServer implements AutoCloseable {

    private final Javalin app;

    public ApiServer(SnapshotBroadcaster broadcaster, List<ApiRoutes> routes) {
        this.app = Javalin.create(config -> config.staticFiles.add("/static", Location.CLASSPATH));

        app.get("/api/health", ctx -> ctx.json(Map.of("status", "ok")));
        routes.forEach(group -> group.register(app));

        app.ws("/ws", ws -> {
            ws.onConnect(broadcaster::register);
            ws.onClose(broadcaster::unregister);
            ws.onError(broadcaster::unregister);
        });

        // El núcleo valida sus argumentos con IllegalArgumentException; para el cliente eso es un 400.
        app.exception(IllegalArgumentException.class, (e, ctx) ->
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", e.getMessage())));
    }

    /** Arranca el servidor y devuelve el puerto real (útil cuando se pide el puerto 0). */
    public int start(int port) {
        app.start(port);
        return app.port();
    }

    @Override
    public void close() {
        app.stop();
    }
}
