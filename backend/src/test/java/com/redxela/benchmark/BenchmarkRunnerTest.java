package com.redxela.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.redxela.common.StateConflictException;
import com.redxela.scheduling.SchedulingPolicy;
import com.redxela.stats.StatsSnapshot;
import com.redxela.stats.StatsTotals;
import com.redxela.testsupport.Await;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** El orquestador se prueba con un ejecutor falso: aquí importa el control de la comparación, no la simulación. */
class BenchmarkRunnerTest {

    private static final BenchmarkParams PARAMS = new BenchmarkParams(20, 1.0, 1);

    private BenchmarkRunner runner;

    @AfterEach
    void tearDown() {
        if (runner != null) {
            runner.close();
        }
    }

    private static PolicyResult resultOf(SchedulingPolicy policy) {
        StatsSnapshot stats = new StatsSnapshot(new StatsTotals(20, 0, 0, 1.0, 1.0, 1), List.of());
        return new PolicyResult(policy, stats, 1_000, 0.5);
    }

    @Test
    void runs_every_policy_in_order_and_ends_done() throws InterruptedException {
        List<SchedulingPolicy> executed = new CopyOnWriteArrayList<>();
        runner = new BenchmarkRunner((policy, trace, progress) -> {
            executed.add(policy);
            progress.accept(trace.size());
            return resultOf(policy);
        });

        runner.start(PARAMS);
        Await.until(() -> runner.snapshot().phase() == BenchmarkPhase.DONE);

        assertThat(executed).containsExactly(SchedulingPolicy.values());
        BenchmarkSnapshot done = runner.snapshot();
        assertThat(done.policiesDone()).isEqualTo(SchedulingPolicy.values().length);
        assertThat(done.results()).extracting(PolicyResult::policy).containsExactly(SchedulingPolicy.values());
        assertThat(done.error()).isNull();
    }

    @Test
    void every_policy_receives_the_same_trace() throws InterruptedException {
        List<List<TraceEntry>> received = new CopyOnWriteArrayList<>();
        runner = new BenchmarkRunner((policy, trace, progress) -> {
            received.add(trace);
            return resultOf(policy);
        });

        runner.start(PARAMS);
        Await.until(() -> runner.snapshot().phase() == BenchmarkPhase.DONE);

        assertThat(received).hasSize(SchedulingPolicy.values().length);
        assertThat(received).allSatisfy(trace -> assertThat(trace).isEqualTo(received.getFirst()));
    }

    @Test
    void publishes_partial_results_while_running() throws InterruptedException {
        CountDownLatch secondPolicyStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<SchedulingPolicy> started = new ArrayList<>();
        runner = new BenchmarkRunner((policy, trace, progress) -> {
            synchronized (started) {
                started.add(policy);
            }
            if (started.size() == 2) {
                secondPolicyStarted.countDown();
                release.await();
            }
            return resultOf(policy);
        });

        runner.start(PARAMS);
        secondPolicyStarted.await();
        Await.until(() -> runner.snapshot().policiesDone() == 1);

        BenchmarkSnapshot partial = runner.snapshot();
        assertThat(partial.phase()).isEqualTo(BenchmarkPhase.RUNNING);
        assertThat(partial.results()).hasSize(1);
        assertThat(partial.currentPolicy()).isEqualTo(SchedulingPolicy.values()[1]);
        release.countDown();
        Await.until(() -> runner.snapshot().phase() == BenchmarkPhase.DONE);
    }

    @Test
    void refuses_to_start_a_second_comparison_while_one_runs() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        runner = new BenchmarkRunner((policy, trace, progress) -> {
            release.await();
            return resultOf(policy);
        });
        runner.start(PARAMS);

        assertThatThrownBy(() -> runner.start(PARAMS)).isInstanceOf(StateConflictException.class);

        release.countDown();
        Await.until(() -> runner.snapshot().phase() == BenchmarkPhase.DONE);
    }

    @Test
    void cancel_stops_the_run_and_keeps_finished_results() throws InterruptedException {
        runner = new BenchmarkRunner((policy, trace, progress) -> {
            if (policy == SchedulingPolicy.values()[1]) {
                new CountDownLatch(1).await(); // se queda esperando hasta que lo cancelen
            }
            return resultOf(policy);
        });
        runner.start(PARAMS);
        Await.until(() -> runner.snapshot().policiesDone() == 1);

        assertThat(runner.cancel()).isTrue();
        Await.until(() -> runner.snapshot().phase() == BenchmarkPhase.CANCELLED);

        assertThat(runner.snapshot().results()).hasSize(1);
    }

    @Test
    void cancel_without_a_running_comparison_returns_false() {
        runner = new BenchmarkRunner((policy, trace, progress) -> resultOf(policy));

        assertThat(runner.cancel()).isFalse();
    }

    @Test
    void a_failure_is_reported_and_the_runner_can_start_again() throws InterruptedException {
        runner = new BenchmarkRunner((policy, trace, progress) -> {
            throw new IllegalStateException("boom");
        });
        runner.start(PARAMS);
        Await.until(() -> runner.snapshot().phase() == BenchmarkPhase.FAILED);

        assertThat(runner.snapshot().error()).isEqualTo("boom");
        runner.start(PARAMS); // tras un fallo se puede volver a intentar
        Await.until(() -> runner.snapshot().phase() == BenchmarkPhase.FAILED);
    }

    @Test
    void starts_idle() {
        runner = new BenchmarkRunner((policy, trace, progress) -> resultOf(policy));

        assertThat(runner.snapshot().phase()).isEqualTo(BenchmarkPhase.IDLE);
    }
}
