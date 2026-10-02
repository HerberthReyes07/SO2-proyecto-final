package com.redxela.api;

import io.javalin.websocket.WsContext;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Envía periódicamente una foto del estado a todos los clientes WebSocket conectados.
 *
 * <p>Usa un único hilo propio, así que la lectura del estado y el envío no dependen de los hilos
 * de Jetty que atienden las peticiones HTTP.
 */
public final class SnapshotBroadcaster implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SnapshotBroadcaster.class);

    private final Supplier<SimulationSnapshot> source;
    private final Set<WsContext> sessions = new CopyOnWriteArraySet<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "snapshot-broadcaster");
        thread.setDaemon(true);
        return thread;
    });

    public SnapshotBroadcaster(Supplier<SimulationSnapshot> source) {
        this.source = source;
    }

    public void start(Duration period) {
        scheduler.scheduleAtFixedRate(this::broadcast, 0, period.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void register(WsContext session) {
        sessions.add(session);
    }

    public void unregister(WsContext session) {
        sessions.remove(session);
    }

    private void broadcast() {
        // Cualquier excepción aquí cancelaría las ejecuciones futuras de scheduleAtFixedRate,
        // por eso se captura todo y se registra.
        try {
            if (sessions.isEmpty()) {
                return;
            }
            SimulationSnapshot snapshot = source.get();
            for (WsContext session : sessions) {
                sendTo(session, snapshot);
            }
        } catch (RuntimeException e) {
            LOG.error("Snapshot broadcast failed", e);
        }
    }

    private void sendTo(WsContext session, SimulationSnapshot snapshot) {
        try {
            session.send(snapshot);
        } catch (RuntimeException e) {
            // Un cliente caído no debe impedir que los demás reciban su foto.
            LOG.warn("Dropping WebSocket session {}: {}", session.sessionId(), e.getMessage());
            sessions.remove(session);
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
