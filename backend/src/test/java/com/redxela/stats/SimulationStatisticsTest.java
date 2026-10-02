package com.redxela.stats;

import static org.assertj.core.api.Assertions.assertThat;

import com.redxela.domain.CompletedOrder;
import com.redxela.domain.ResourceType;
import com.redxela.domain.ServiceLevel;
import com.redxela.resources.ResourceRegistry;
import com.redxela.sim.SimClock;
import com.redxela.testsupport.FakeOperation;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SimulationStatisticsTest {

    private final SimClock clock = new SimClock();
    private final ResourceRegistry resources = new ResourceRegistry();
    private SimulationStatistics statistics;
    private Duration start;

    @BeforeEach
    void setUp() {
        clock.pause(); // el reloj queda fijo: los instantes de los cálculos son exactos
        start = clock.now();
        statistics = new SimulationStatistics(clock, resources);
    }

    private static CompletedOrder order(String id, ServiceLevel level, long waitSeconds, long processingSeconds) {
        return new CompletedOrder(id, level, 5, Duration.ofSeconds(waitSeconds), Duration.ofSeconds(processingSeconds));
    }

    private LevelStats level(StatsSnapshot snapshot, ServiceLevel level) {
        return snapshot.levels().stream().filter(stats -> stats.level() == level).findFirst().orElseThrow();
    }

    @Test
    void with_no_orders_everything_is_zero_and_never_not_a_number() {
        StatsSnapshot snapshot = statistics.snapshot(start.plusMinutes(10));

        assertThat(snapshot.levels()).extracting(LevelStats::level).containsExactly(ServiceLevel.values());
        assertThat(snapshot.totals().completed()).isZero();
        assertThat(snapshot.totals().avgWaitMillis()).isZero();
        assertThat(snapshot.totals().onTimeRatio()).isZero();
        assertThat(snapshot.totals().throughputPerMinute()).isZero();
        assertThat(snapshot.levels()).allSatisfy(stats -> {
            assertThat(stats.completed()).isZero();
            assertThat(Double.isNaN(stats.onTimeRatio())).isFalse();
        });
    }

    @Test
    void averages_maximum_and_on_time_ratio_are_computed_per_level() {
        statistics.record(order("A", ServiceLevel.EXPRESS, 30, 100));   // objetivo 1 min: a tiempo
        statistics.record(order("B", ServiceLevel.EXPRESS, 120, 200));  // tarde
        statistics.record(order("C", ServiceLevel.ECONOMY, 3600, 50));  // objetivo 2 h: a tiempo

        StatsSnapshot snapshot = statistics.snapshot(start.plusMinutes(10));

        LevelStats express = level(snapshot, ServiceLevel.EXPRESS);
        assertThat(express.completed()).isEqualTo(2);
        assertThat(express.avgWaitMillis()).isEqualTo(75_000);
        assertThat(express.maxWaitMillis()).isEqualTo(120_000);
        assertThat(express.avgProcessingMillis()).isEqualTo(150_000);
        assertThat(express.onTimeRatio()).isEqualTo(0.5);
        assertThat(level(snapshot, ServiceLevel.ECONOMY).onTimeRatio()).isEqualTo(1.0);
        assertThat(level(snapshot, ServiceLevel.STANDARD).completed()).isZero();
    }

    @Test
    void an_order_that_waited_exactly_its_target_counts_as_on_time() {
        statistics.record(order("A", ServiceLevel.EXPRESS, 60, 10));

        assertThat(level(statistics.snapshot(start), ServiceLevel.EXPRESS).onTimeRatio()).isEqualTo(1.0);
    }

    @Test
    void totals_combine_every_level_and_report_the_throughput() {
        statistics.record(order("A", ServiceLevel.EXPRESS, 30, 100));   // objetivo 1 min: a tiempo
        statistics.record(order("B", ServiceLevel.EXPRESS, 90, 300));   // tarde
        statistics.record(order("C", ServiceLevel.STANDARD, 60, 200));  // objetivo 30 min: a tiempo
        statistics.record(order("D", ServiceLevel.STANDARD, 60, 200));  // a tiempo

        StatsTotals totals = statistics.snapshot(start.plusMinutes(2)).totals();

        assertThat(totals.completed()).isEqualTo(4);
        assertThat(totals.avgWaitMillis()).isEqualTo(60_000);
        assertThat(totals.avgProcessingMillis()).isEqualTo(200_000);
        assertThat(totals.onTimeRatio()).isEqualTo(0.75); // 3 de 4: solo B llegó tarde
        assertThat(totals.elapsedMillis()).isEqualTo(120_000);
        assertThat(totals.throughputPerMinute()).isEqualTo(2.0); // 4 pedidos en 2 minutos simulados
    }

    @Test
    void resetting_clears_the_counters_and_restarts_the_elapsed_time() {
        statistics.record(order("A", ServiceLevel.EXPRESS, 30, 100));

        statistics.reset();
        StatsSnapshot snapshot = statistics.snapshot(clock.now().plusMinutes(1));

        assertThat(snapshot.totals().completed()).isZero();
        assertThat(snapshot.totals().elapsedMillis()).isEqualTo(60_000);
        assertThat(level(snapshot, ServiceLevel.EXPRESS).maxWaitMillis()).isZero();
    }

    @Test
    void resetting_also_restarts_the_resource_utilization_measurement() throws Exception {
        // Con el reloj real: la unidad está ocupada un rato y luego ociosa otro, así que antes del
        // reinicio la ocupación es intermedia; tras reiniciar se mide desde cero y vale 0.
        ResourcePoolHandle scanner = new ResourcePoolHandle(resources);
        scanner.occupyFor(40);
        Thread.sleep(40);
        assertThat(scanner.utilization()).isBetween(0.2, 0.8);

        statistics.reset();

        assertThat(scanner.utilization()).isLessThan(0.1);
    }

    @Test
    void recording_from_many_threads_loses_no_order() throws Exception {
        int threads = 8;
        int perThread = 500;
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int thread = t;
                tasks.add(executor.submit(() -> {
                    for (int i = 0; i < perThread; i++) {
                        statistics.record(order("T" + thread + "-" + i, ServiceLevel.values()[i % 5], 1, 2));
                    }
                }));
            }
            for (Future<?> task : tasks) {
                task.get();
            }
        }

        StatsSnapshot snapshot = statistics.snapshot(start.plusMinutes(1));

        assertThat(snapshot.totals().completed()).isEqualTo((long) threads * perThread);
        assertThat(snapshot.levels()).allSatisfy(stats -> assertThat(stats.completed()).isEqualTo(800));
        assertThat(snapshot.totals().avgWaitMillis()).isEqualTo(1_000);   // todos esperaron 1 s
        assertThat(snapshot.totals().avgProcessingMillis()).isEqualTo(2_000);
    }
}

/** Ayuda para el test: ocupa el escáner durante un tiempo y consulta su ocupación. */
final class ResourcePoolHandle {

    private final ResourceRegistry resources;

    ResourcePoolHandle(ResourceRegistry resources) {
        this.resources = resources;
    }

    void occupyFor(long millis) throws Exception {
        FakeOperation holder = new FakeOperation("holder");
        resources.pool(ResourceType.SCANNER).acquire(holder);
        Thread.sleep(millis);
        resources.pool(ResourceType.SCANNER).release(holder);
    }

    double utilization() {
        return resources.pool(ResourceType.SCANNER).snapshot().utilization();
    }
}
