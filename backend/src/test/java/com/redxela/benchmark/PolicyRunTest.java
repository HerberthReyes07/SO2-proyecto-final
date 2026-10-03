package com.redxela.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.redxela.catalog.ProductCatalog;
import com.redxela.customers.CustomerRegistry;
import com.redxela.domain.ServiceLevel;
import com.redxela.generator.WorkloadProfile;
import com.redxela.scheduling.SchedulingPolicy;
import com.redxela.stats.LevelStats;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Corridas reales (con hilos y reloj acelerado). Usan una carga saturada (3 pedidos/min contra ~1 de capacidad), con llegadas repartidas en decenas de minutos para que el envejecimiento tenga efecto: es donde las políticas se separan
 * con márgenes muy superiores al ruido de planificación de hilos, así que las comparaciones son estables.
 */
class PolicyRunTest {

    private static final int ORDERS = 80;
    private static List<TraceEntry> trace;

    @BeforeAll
    static void buildTrace() {
        trace = WorkloadTrace.generate(WorkloadProfile.defaults(), ProductCatalog.defaultCatalog(),
                CustomerRegistry.withSampleCustomers().all(), new BenchmarkParams(ORDERS, 3.0, 42));
    }

    private static PolicyResult run(SchedulingPolicy policy) throws InterruptedException {
        return PolicyRun.execute(policy, trace, completed -> { });
    }

    private static LevelStats level(PolicyResult result, ServiceLevel level) {
        return result.stats().levels().stream().filter(stats -> stats.level() == level).findFirst().orElseThrow();
    }

    @Test
    void dispatches_every_order_of_the_trace_and_reports_progress() throws InterruptedException {
        AtomicInteger lastProgress = new AtomicInteger();

        PolicyResult result = PolicyRun.execute(SchedulingPolicy.FIFO, trace, lastProgress::set);

        assertThat(result.stats().totals().completed()).isEqualTo(ORDERS);
        assertThat(lastProgress.get()).isEqualTo(ORDERS);
        assertThat(result.workerUtilization()).isBetween(0.0, 1.0);
        assertThat(result.durationMillis()).isPositive();
    }

    @Test
    void policies_show_the_trade_offs_the_theory_predicts() throws InterruptedException {
        PolicyResult fifo = run(SchedulingPolicy.FIFO);
        PolicyResult strict = run(SchedulingPolicy.STRICT_PRIORITY);
        PolicyResult aging = run(SchedulingPolicy.AGING_PRIORITY);
        PolicyResult sjf = run(SchedulingPolicy.SHORTEST_JOB_FIRST);

        // Prioridad estricta: el nivel más urgente espera una fracción de lo que espera con FIFO.
        assertThat(level(strict, ServiceLevel.EXPRESS).avgWaitMillis())
                .isLessThan(level(fifo, ServiceLevel.EXPRESS).avgWaitMillis() / 3);
        // ...a costa del nivel más bajo, que espera más que con FIFO.
        assertThat(level(strict, ServiceLevel.ECONOMY).avgWaitMillis())
                .isGreaterThan(level(fifo, ServiceLevel.ECONOMY).avgWaitMillis());
        // El envejecimiento rescata al nivel más bajo respecto de la prioridad estricta.
        assertThat(level(aging, ServiceLevel.ECONOMY).avgWaitMillis())
                .isLessThan(level(strict, ServiceLevel.ECONOMY).avgWaitMillis());
        // SJF minimiza la espera promedio general (resultado clásico de planificación).
        assertThat(sjf.stats().totals().avgWaitMillis()).isLessThan(fifo.stats().totals().avgWaitMillis());
    }

    @Test
    void the_same_policy_run_twice_gives_nearly_the_same_result() throws InterruptedException {
        PolicyResult first = run(SchedulingPolicy.FIFO);
        PolicyResult second = run(SchedulingPolicy.FIFO);

        // Si una política difiriera de sí misma más que de las demás, la comparación no serviría.
        assertThat((double) second.stats().totals().avgWaitMillis())
                .isCloseTo(first.stats().totals().avgWaitMillis(), within(first.stats().totals().avgWaitMillis() * 0.05));
        assertThat(second.stats().totals().completed()).isEqualTo(first.stats().totals().completed());
    }

    @Test
    void cancelling_mid_run_tears_down_every_thread() throws InterruptedException {
        List<Throwable> failures = new ArrayList<>();
        long before = simulationThreads();
        Thread runner = Thread.ofPlatform().start(() -> {
            try {
                PolicyRun.execute(SchedulingPolicy.FIFO, trace, completed -> { });
            } catch (InterruptedException expected) {
                // La interrupción es la forma de cancelar: es el resultado esperado.
            } catch (RuntimeException e) {
                failures.add(e);
            }
        });
        Thread.sleep(1_000);
        runner.interrupt();
        runner.join(5_000);

        assertThat(runner.isAlive()).isFalse();
        assertThat(failures).isEmpty();
        assertThat(simulationThreads()).as("threads left by the cancelled run").isEqualTo(before);
    }

    /** Hilos del despachador y de los pedidos; se compara contra una línea base porque otros tests pueden dejar los suyos. */
    private static long simulationThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().startsWith("dispatcher") || thread.getName().startsWith("order-"))
                .filter(Thread::isAlive)
                .count();
    }
}
