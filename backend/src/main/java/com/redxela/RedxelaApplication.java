package com.redxela;

import com.redxela.api.ApiRoutes;
import com.redxela.api.ApiServer;
import com.redxela.api.ClockRoutes;
import com.redxela.api.SimulationSnapshot;
import com.redxela.api.SnapshotBroadcaster;
import com.redxela.resources.ResourceRegistry;
import com.redxela.sim.SimClock;
import java.time.Duration;
import java.util.List;

/** Punto de entrada: construye los componentes, los conecta entre sí y arranca el servidor. */
public final class RedxelaApplication {

    private static final int DEFAULT_PORT = 7070;
    private static final Duration SNAPSHOT_PERIOD = Duration.ofMillis(300);

    private RedxelaApplication() {
    }

    public static void main(String[] args) {
        SimClock clock = new SimClock();
        ResourceRegistry registry = new ResourceRegistry();

        SnapshotBroadcaster broadcaster = new SnapshotBroadcaster(
                () -> new SimulationSnapshot(clock.snapshot(), registry.snapshots()));
        List<ApiRoutes> routes = List.of(new ClockRoutes(clock));
        ApiServer server = new ApiServer(broadcaster, routes);

        // Al cerrar la JVM (Ctrl+C) se detienen ordenadamente los hilos propios y el servidor.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            broadcaster.close();
            server.close();
        }, "shutdown"));

        broadcaster.start(SNAPSHOT_PERIOD);
        server.start(Integer.getInteger("redxela.port", DEFAULT_PORT));
    }
}
