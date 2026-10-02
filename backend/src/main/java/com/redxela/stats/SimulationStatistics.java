package com.redxela.stats;

import com.redxela.domain.CompletedOrder;
import com.redxela.domain.ServiceLevel;
import com.redxela.resources.ResourceRegistry;
import com.redxela.sim.SimClock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Estadísticas de la simulación: acumula los pedidos despachados y, al reiniciarse, también reinicia
 * la medición de ocupación de los recursos para que todo se mida desde el mismo instante.
 *
 * <h3>Sincronización</h3>
 * Los pedidos terminan en hilos distintos, y cada registro actualiza varios contadores a la vez
 * (cantidad, sumas, máximo). Un {@link ReentrantLock} los protege juntos para que un snapshot
 * nunca vea, por ejemplo, la cantidad ya incrementada pero la suma todavía sin actualizar.
 */
public final class SimulationStatistics {

    private final SimClock clock;
    private final ResourceRegistry resources;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<ServiceLevel, Accumulator> byLevel = new EnumMap<>(ServiceLevel.class);
    private Duration since;

    public SimulationStatistics(SimClock clock, ResourceRegistry resources) {
        this.clock = clock;
        this.resources = resources;
        this.since = clock.now();
        resetAccumulators();
    }

    /** Registra un pedido despachado. Seguro entre hilos. */
    public void record(CompletedOrder order) {
        lock.lock();
        try {
            byLevel.get(order.level()).add(order);
        } finally {
            lock.unlock();
        }
    }

    /** Foto coherente de las estadísticas en el instante simulado {@code now}. */
    public StatsSnapshot snapshot(Duration now) {
        lock.lock();
        try {
            List<LevelStats> levels = new ArrayList<>();
            Accumulator total = new Accumulator();
            for (ServiceLevel level : ServiceLevel.values()) {
                Accumulator accumulator = byLevel.get(level);
                levels.add(accumulator.toLevelStats(level));
                total.merge(accumulator);
            }
            long elapsedMillis = Math.max(0, now.minus(since).toMillis());
            return new StatsSnapshot(total.toTotals(elapsedMillis), levels);
        } finally {
            lock.unlock();
        }
    }

    /** Pone a cero los contadores y la medición de ocupación; lo que se mida desde aquí parte de cero. */
    public void reset() {
        lock.lock();
        try {
            since = clock.now();
            resetAccumulators();
            resources.resetUsage();
        } finally {
            lock.unlock();
        }
    }

    private void resetAccumulators() {
        for (ServiceLevel level : ServiceLevel.values()) {
            byLevel.put(level, new Accumulator());
        }
    }

    /** Sumas y contadores de un grupo de pedidos; no es seguro entre hilos por sí mismo (lo protege el lock de la clase). */
    private static final class Accumulator {
        private long completed;
        private long onTime;
        private long waitMillisSum;
        private long processingMillisSum;
        private long maxWaitMillis;

        void add(CompletedOrder order) {
            long waitMillis = order.waited().toMillis();
            completed++;
            waitMillisSum += waitMillis;
            processingMillisSum += order.processing().toMillis();
            maxWaitMillis = Math.max(maxWaitMillis, waitMillis);
            if (order.metTarget()) {
                onTime++;
            }
        }

        void merge(Accumulator other) {
            completed += other.completed;
            onTime += other.onTime;
            waitMillisSum += other.waitMillisSum;
            processingMillisSum += other.processingMillisSum;
            maxWaitMillis = Math.max(maxWaitMillis, other.maxWaitMillis);
        }

        LevelStats toLevelStats(ServiceLevel level) {
            return new LevelStats(level, completed, average(waitMillisSum), maxWaitMillis,
                    average(processingMillisSum), ratio());
        }

        StatsTotals toTotals(long elapsedMillis) {
            double minutes = elapsedMillis / 60_000.0;
            double throughput = minutes > 0 ? completed / minutes : 0.0;
            return new StatsTotals(completed, average(waitMillisSum), average(processingMillisSum),
                    ratio(), throughput, elapsedMillis);
        }

        // Con cero pedidos los promedios y la proporción valen 0 (no NaN): así el JSON siempre es válido.
        private long average(long sum) {
            return completed == 0 ? 0 : sum / completed;
        }

        private double ratio() {
            return completed == 0 ? 0.0 : (double) onTime / completed;
        }
    }
}
