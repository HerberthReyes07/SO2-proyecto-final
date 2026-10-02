package com.redxela;

import com.redxela.api.ApiServer;
import com.redxela.api.SimulationSnapshot;
import com.redxela.api.SnapshotBroadcaster;
import com.redxela.resources.ResourceRegistry;
import java.time.Duration;

/** Punto de entrada: construye los componentes, los conecta entre sí y arranca el servidor. */
public final class RedxelaApplication {

    private static final int DEFAULT_PORT = 7070;
    private static final Duration SNAPSHOT_PERIOD = Duration.ofMillis(300);

    private RedxelaApplication() {
    }

    public static void main(String[] args) {
        ResourceRegistry registry = new ResourceRegistry();

        SnapshotBroadcaster broadcaster = new SnapshotBroadcaster(
                () -> new SimulationSnapshot(registry.snapshots()));
        ApiServer server = new ApiServer(broadcaster);

        // Al cerrar la JVM (Ctrl+C) se detienen ordenadamente los hilos propios y el servidor.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            broadcaster.close();
            server.close();
        }, "shutdown"));

        broadcaster.start(SNAPSHOT_PERIOD);
        server.start(Integer.getInteger("redxela.port", DEFAULT_PORT));
    }
}
