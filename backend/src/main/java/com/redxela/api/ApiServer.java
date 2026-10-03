package com.redxela.api;

import com.redxela.common.StateConflictException;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.http.staticfiles.Location;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Servidor HTTP: expone la API REST, el WebSocket de estado y los archivos estáticos del frontend.
 *
 * <p>Es la única clase del backend que conoce Javalin; el núcleo de la simulación es Java plano.
 */
public final class ApiServer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ApiServer.class);
    private static final String STATIC_DIRECTORY = "/static";

    private final Javalin app;

    public ApiServer(SnapshotBroadcaster broadcaster, List<ApiRoutes> routes) {
        this.app = Javalin.create(config -> {
            // Javalin falla al arrancar si la carpeta no existe, y puede no existir (JAR sin frontend, tests, clon
            // recién bajado): la API debe funcionar igual, así que los estáticos son opcionales.
            if (ApiServer.class.getResource(STATIC_DIRECTORY) != null) {
                config.staticFiles.add(STATIC_DIRECTORY, Location.CLASSPATH);
            } else {
                LOG.warn("The frontend was not built: serving the API only");
            }
        });

        app.get("/api/health", ctx -> ctx.json(Map.of("status", "ok")));
        routes.forEach(group -> group.register(app));

        app.ws("/ws", ws -> {
            ws.onConnect(broadcaster::register);
            ws.onClose(broadcaster::unregister);
            ws.onError(broadcaster::unregister);
        });

        // La acción pedida ya no corresponde al estado del sistema (ej. resolver un deadlock que ya no existe): 409.
        app.exception(StateConflictException.class, (e, ctx) ->
                ctx.status(HttpStatus.CONFLICT).json(Map.of("error", e.getMessage())));

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
