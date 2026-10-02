package com.redxela.api;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import java.util.Map;

/**
 * Servidor HTTP: expone la API REST, el WebSocket de estado y los archivos estáticos del frontend.
 *
 * <p>Es la única clase del backend que conoce Javalin; el núcleo de la simulación es Java plano.
 */
public final class ApiServer implements AutoCloseable {

    private final Javalin app;

    public ApiServer(SnapshotBroadcaster broadcaster) {
        this.app = Javalin.create(config -> config.staticFiles.add("/static", Location.CLASSPATH));

        app.get("/api/health", ctx -> ctx.json(Map.of("status", "ok")));

        app.ws("/ws", ws -> {
            ws.onConnect(broadcaster::register);
            ws.onClose(broadcaster::unregister);
            ws.onError(broadcaster::unregister);
        });
    }

    public void start(int port) {
        app.start(port);
    }

    @Override
    public void close() {
        app.stop();
    }
}
