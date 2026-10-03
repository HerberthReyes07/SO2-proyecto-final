package com.redxela.deadlock;

import com.redxela.deadlock.DeadlockAnalyzer.Analysis;
import com.redxela.deadlock.DeadlockAnalyzer.Participant;
import com.redxela.resources.ResourceRegistry;
import com.redxela.sim.SimClock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Revisa periódicamente si el sistema está en deadlock, lo reporta y ejecuta su resolución manual.
 *
 * <h3>Confirmación por estabilidad</h3>
 * Cada pool tiene su propio lock, así que leer todos los pools no da una foto <b>atómica</b> del
 * sistema: una lectura podría ver a una operación reteniendo un recurso (antes de que lo suelte) y
 * esperando otro (después de pedirlo), y armar un ciclo que nunca existió. Un deadlock real, en
 * cambio, es <b>estable</b>: nadie del conjunto puede avanzar, así que no cambia. Por eso solo se
 * declara cuando el mismo conjunto de operaciones aparece en dos revisiones consecutivas.
 *
 * <h3>Recuperación por apropiación</h3>
 * Resolver es expulsar a una de las dos operaciones en conflicto ({@code preempt}): su hilo recibe
 * {@code OperationPreemptedException}, libera todo lo que retiene y vuelve a la lista de espera.
 * Eso quita la condición "sin apropiación" de Coffman para esa operación y rompe el ciclo.
 *
 * <h3>Sincronización</h3>
 * Un único lock del detector serializa las revisiones y las resoluciones, de modo que una
 * resolución nunca actúa sobre un reporte que una revisión está reemplazando.
 */
public final class DeadlockDetector implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DeadlockDetector.class);

    /** Resultado de una resolución. */
    public record Resolution(String continued, String returnedToWaiting) {
    }

    /** Un deadlock confirmado, con las operaciones vivas necesarias para poder expulsar a una. */
    private record Confirmed(String key, DeadlockReport report, Map<String, Participant> participantsById) {
    }

    private final ResourceRegistry resources;
    private final SimClock clock;
    private final AcquisitionPolicy policy;
    private final Duration scanPeriod;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "deadlock-detector");
        thread.setDaemon(true);
        return thread;
    });

    // Estado protegido por `this` (los métodos que lo tocan son synchronized).
    private String previousKey;
    private Confirmed confirmed;
    private long detected;
    private long resolved;

    public DeadlockDetector(ResourceRegistry resources, SimClock clock, AcquisitionPolicy policy, Duration scanPeriod) {
        this.resources = resources;
        this.clock = clock;
        this.policy = policy;
        this.scanPeriod = scanPeriod;
    }

    public void start() {
        scheduler.scheduleWithFixedDelay(this::scanSafely, scanPeriod.toMillis(), scanPeriod.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Una revisión; lo hace público para poder ejecutarla a voluntad en los tests, sin esperar al reloj. */
    public synchronized void scan() {
        Optional<Analysis> found = DeadlockAnalyzer.analyze(resources.operations());
        if (found.isEmpty()) {
            previousKey = null;
            confirmed = null;
            return;
        }
        Analysis analysis = found.get();
        String key = analysis.key();
        if (key.equals(previousKey)) {
            confirm(analysis, key);
        }
        previousKey = key;
    }

    /**
     * Resuelve el deadlock vigente dejando continuar a una de las dos operaciones en conflicto; la otra
     * es expulsada y vuelve a la lista de espera.
     *
     * @param continuingId operación que conserva los recursos; debe ser una de las dos en conflicto
     * @throws StateConflictException   si no hay deadlock vigente, o la víctima ya dejó de esperar
     * @throws IllegalArgumentException si {@code continuingId} no es una de las dos operaciones en conflicto
     */
    public synchronized Resolution resolve(String continuingId) {
        if (confirmed == null) {
            throw new StateConflictException("There is no deadlock to resolve");
        }
        ConflictPair pair = confirmed.report().conflict();
        if (!continuingId.equals(pair.waiter()) && !continuingId.equals(pair.holder())) {
            throw new IllegalArgumentException(
                    "Operation " + continuingId + " is not one of the two in conflict (" + pair.waiter() + ", " + pair.holder() + ")");
        }
        String victimId = continuingId.equals(pair.waiter()) ? pair.holder() : pair.waiter();
        Participant victim = confirmed.participantsById().get(victimId);

        boolean expelled = resources.pool(victim.waitingFor()).preempt(victim.operation());
        if (!expelled) {
            // Mientras tanto la víctima recibió su recurso: el deadlock se deshizo solo; que se vuelva a evaluar.
            confirmed = null;
            previousKey = null;
            throw new StateConflictException("Operation " + victimId + " is no longer waiting: the deadlock has changed");
        }
        resolved++;
        confirmed = null;
        previousKey = null; // el siguiente deadlock, si lo hay, debe volver a confirmarse
        LOG.info("Deadlock resolved: {} continues, {} returns to waiting", continuingId, victimId);
        return new Resolution(continuingId, victimId);
    }

    public synchronized DeadlockSnapshot snapshot() {
        return new DeadlockSnapshot(policy.mode(), confirmed == null ? null : confirmed.report(), detected, resolved);
    }

    // ---- Internos ----

    /** Registra el deadlock como confirmado; si es el mismo que ya estaba, conserva su id y su hora de detección. */
    private void confirm(Analysis analysis, String key) {
        List<DeadlockedOperation> operations = analysis.deadlocked().stream()
                .map(participant -> new DeadlockedOperation(participant.id(), participant.operation().kind(),
                        List.copyOf(participant.held()), participant.waitingFor()))
                .toList();
        ConflictPair conflict = new ConflictPair(analysis.waiter().id(), analysis.holder().id());

        boolean sameAsBefore = confirmed != null && confirmed.key().equals(key);
        String id;
        long detectedAt;
        if (sameAsBefore) {
            id = confirmed.report().id();
            detectedAt = confirmed.report().detectedAtMillis();
        } else {
            detected++;
            id = String.format("DL-%04d", detected);
            detectedAt = clock.now().toMillis();
            LOG.warn("Deadlock {} confirmed among {}", id, analysis.key());
        }
        Map<String, Participant> byId = new HashMap<>();
        analysis.deadlocked().forEach(participant -> byId.put(participant.id(), participant));
        confirmed = new Confirmed(key, new DeadlockReport(id, detectedAt, operations, analysis.resources(), conflict), byId);
    }

    private void scanSafely() {
        // Una excepción aquí cancelaría las revisiones futuras de scheduleWithFixedDelay.
        try {
            scan();
        } catch (RuntimeException e) {
            LOG.error("Deadlock scan failed", e);
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
