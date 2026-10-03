package com.redxela.benchmark;

import com.redxela.catalog.ProductCatalog;
import com.redxela.common.StateConflictException;
import com.redxela.customers.CustomerRegistry;
import com.redxela.generator.WorkloadProfile;
import com.redxela.scheduling.SchedulingPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compara todas las políticas de atención con la misma carga, de a una y en segundo plano.
 *
 * <p>Corre las políticas <b>en secuencia y no en paralelo</b>: cada corrida usa varios hilos y, si compitieran
 * entre sí por el procesador, el ruido de planificación de hilos distorsionaría justamente lo que se mide. Cada
 * resultado se publica apenas termina, así la interfaz va llenando la tabla sin esperar al final.
 *
 * <h3>Sincronización</h3>
 * El estado visible es un registro inmutable en un campo {@code volatile}: solo el hilo de la comparación lo
 * reemplaza y cualquier lector (el que arma los snapshots) ve una foto coherente sin locks. Iniciar y cancelar
 * se serializan con el lock del objeto.
 */
public final class BenchmarkRunner implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(BenchmarkRunner.class);
    private static final long SHUTDOWN_WAIT_MILLIS = 2_000;

    private final PolicyExecutor executor;
    private volatile BenchmarkSnapshot snapshot = BenchmarkSnapshot.idle();
    private Thread thread;

    public BenchmarkRunner(PolicyExecutor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /** Estado actual de la comparación. */
    public BenchmarkSnapshot snapshot() {
        return snapshot;
    }

    /**
     * Arranca una comparación nueva y retorna de inmediato.
     *
     * @throws StateConflictException si ya hay una comparación en marcha
     */
    public synchronized void start(BenchmarkParams params) {
        if (snapshot.phase() == BenchmarkPhase.RUNNING) {
            throw new StateConflictException("A comparison is already running");
        }
        publish(new BenchmarkSnapshot(BenchmarkPhase.RUNNING, params, null, 0, SchedulingPolicy.values().length, 0, List.of(), null));
        thread = Thread.ofPlatform().name("benchmark").start(() -> run(params));
    }

    /**
     * Cancela la comparación en marcha; conserva los resultados de las políticas que ya terminaron.
     *
     * @return {@code true} si había una comparación en marcha
     */
    public synchronized boolean cancel() {
        if (snapshot.phase() != BenchmarkPhase.RUNNING || thread == null) {
            return false;
        }
        thread.interrupt();
        return true;
    }

    private void run(BenchmarkParams params) {
        List<PolicyResult> results = new ArrayList<>();
        try {
            // Los clientes de la traza son los de ejemplo, que cubren todos los niveles de servicio.
            List<TraceEntry> trace = WorkloadTrace.generate(WorkloadProfile.defaults(), ProductCatalog.defaultCatalog(),
                    CustomerRegistry.withSampleCustomers().all(), params);
            for (SchedulingPolicy policy : SchedulingPolicy.values()) {
                publishRunning(params, policy, results, 0);
                PolicyResult result = executor.execute(policy, trace, completed -> publishRunning(params, policy, results, completed));
                results.add(result);
                LOG.info("Benchmark: {} finished in {} simulated minutes", policy, result.durationMillis() / 60_000);
            }
            publish(finished(BenchmarkPhase.DONE, params, results, null));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            publish(finished(BenchmarkPhase.CANCELLED, params, results, null));
        } catch (RuntimeException e) {
            LOG.error("Benchmark failed", e);
            publish(finished(BenchmarkPhase.FAILED, params, results, e.getMessage()));
        }
    }

    private void publishRunning(BenchmarkParams params, SchedulingPolicy current, List<PolicyResult> results, int completed) {
        publish(new BenchmarkSnapshot(BenchmarkPhase.RUNNING, params, current, results.size(),
                SchedulingPolicy.values().length, completed, results, null));
    }

    private static BenchmarkSnapshot finished(BenchmarkPhase phase, BenchmarkParams params, List<PolicyResult> results, String error) {
        return new BenchmarkSnapshot(phase, params, null, results.size(), SchedulingPolicy.values().length, 0, results, error);
    }

    private void publish(BenchmarkSnapshot next) {
        this.snapshot = next;
    }

    @Override
    public void close() {
        Thread running;
        synchronized (this) {
            running = thread;
        }
        if (running == null) {
            return;
        }
        running.interrupt();
        try {
            running.join(SHUTDOWN_WAIT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
