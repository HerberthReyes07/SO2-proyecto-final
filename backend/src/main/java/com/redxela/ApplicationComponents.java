package com.redxela;

import com.redxela.api.ApiServer;
import com.redxela.customers.CustomerRegistry;
import com.redxela.sim.SimClock;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Raíz de la aplicación: une una {@link Simulation} con el servidor HTTP que la expone, y sabe reiniciarla.
 * Los tests de integración usan esta misma clase, así prueban el cableado real.
 *
 * <h3>Reinicio</h3>
 * Reiniciar es cerrar el servidor y la simulación, y levantar otros dos nuevos en el mismo puerto. Los clientes
 * conectados por WebSocket se reconectan solos. Se conservan las cuentas de cliente (son datos maestros, como el
 * catálogo); todo lo demás vuelve al estado inicial.
 *
 * <h3>Sincronización</h3>
 * {@code simulation} y {@code server} cambian al reiniciar, por eso se leen y escriben con el lock del objeto
 * ({@code synchronized}). {@code resetting} evita encolar reinicios mientras hay uno en marcha (doble clic).
 */
public final class ApplicationComponents implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationComponents.class);
    /**
     * Margen para que la respuesta "202" de la petición de reinicio llegue al cliente antes de apagar el servidor
     * que la envía. Javalin escribe la respuesta cuando el manejador ya retornó, y si el servidor se detiene en ese
     * instante la conexión se corta y el navegador ve un error aunque el reinicio sí ocurra.
     */
    private static final long RESPONSE_DELIVERY_GRACE_MILLIS = 300;

    private final CustomerRegistry customers = CustomerRegistry.withSampleCustomers();
    private final AtomicBoolean resetting = new AtomicBoolean();
    private Simulation simulation;
    private ApiServer server;
    private int port;
    private boolean closed;

    public ApplicationComponents() {
        this.simulation = new Simulation(customers, this::requestReset);
        this.server = new ApiServer(simulation.broadcaster(), simulation.routes());
    }

    /** Arranca el servidor y devuelve el puerto real (útil con el puerto 0 en los tests). */
    public synchronized int start(int requestedPort) {
        simulation.start();
        port = server.start(requestedPort);
        return port;
    }

    public synchronized SimClock clock() {
        return simulation.clock();
    }

    /** Pide un reinicio y retorna ya: se hace en otro hilo porque detiene el servidor que atiende esta petición. */
    private void requestReset() {
        if (resetting.compareAndSet(false, true)) {
            Thread.ofPlatform().name("reset").start(this::resetAfterResponse);
        }
    }

    private void resetAfterResponse() {
        try {
            Thread.sleep(RESPONSE_DELIVERY_GRACE_MILLIS);
        } catch (InterruptedException e) {
            // Alguien interrumpe este hilo para que no siga: se respeta y no se reinicia.
            Thread.currentThread().interrupt();
            resetting.set(false);
            return;
        }
        reset();
    }

    private synchronized void reset() {
        try {
            if (closed) {
                return;
            }
            LOG.info("Resetting the simulation");
            stopCurrent();
            // Mismo puerto que antes: el navegador sigue apuntando a la misma dirección y su WebSocket se reconecta.
            simulation = new Simulation(customers, this::requestReset);
            server = new ApiServer(simulation.broadcaster(), simulation.routes());
            simulation.start();
            server.start(port);
        } catch (RuntimeException e) {
            // Si falla a medias no queda nada que servir; se deja constancia para diagnosticarlo.
            LOG.error("The simulation could not be reset", e);
        } finally {
            resetting.set(false);
        }
    }

    private void stopCurrent() {
        simulation.stopPublishing();
        server.close();
        simulation.close();
    }

    @Override
    public synchronized void close() {
        closed = true;
        stopCurrent();
    }
}
