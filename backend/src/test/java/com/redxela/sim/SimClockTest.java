package com.redxela.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class SimClockTest {

    private static final long ONE_SECOND_NANOS = TimeUnit.SECONDS.toNanos(1);

    /** Conversión y pausa: usan un tiempo real falso, así que son instantáneas y deterministas. */
    @Nested
    class TimeConversion {

        private final AtomicLong fakeRealNanos = new AtomicLong();

        private SimClock clockAt(double speed) {
            return new SimClock(fakeRealNanos::get, speed);
        }

        @Test
        void starts_at_zero() {
            assertThat(clockAt(60).now()).isEqualTo(Duration.ZERO);
        }

        @Test
        void scales_real_time_by_the_speed_factor() {
            SimClock clock = clockAt(60);

            fakeRealNanos.addAndGet(ONE_SECOND_NANOS);

            assertThat(clock.now()).isEqualTo(Duration.ofMinutes(1));
        }

        @Test
        void changing_speed_keeps_the_time_already_elapsed() {
            SimClock clock = clockAt(60);
            fakeRealNanos.addAndGet(ONE_SECOND_NANOS); // 1 min simulado

            clock.setSpeed(10);
            fakeRealNanos.addAndGet(ONE_SECOND_NANOS); // +10 s simulados

            assertThat(clock.now()).isEqualTo(Duration.ofMinutes(1).plusSeconds(10));
        }

        @Test
        void paused_clock_does_not_advance() {
            SimClock clock = clockAt(60);
            fakeRealNanos.addAndGet(ONE_SECOND_NANOS);
            clock.pause();
            Duration frozen = clock.now();

            fakeRealNanos.addAndGet(5 * ONE_SECOND_NANOS);

            assertThat(clock.now()).isEqualTo(frozen).isEqualTo(Duration.ofMinutes(1));
        }

        @Test
        void resumed_clock_continues_without_counting_the_paused_time() {
            SimClock clock = clockAt(60);
            fakeRealNanos.addAndGet(ONE_SECOND_NANOS);
            clock.pause();
            fakeRealNanos.addAndGet(5 * ONE_SECOND_NANOS);

            clock.resume();
            fakeRealNanos.addAndGet(ONE_SECOND_NANOS);

            assertThat(clock.now()).isEqualTo(Duration.ofMinutes(2));
        }

        @Test
        void pause_and_resume_are_idempotent() {
            SimClock clock = clockAt(60);

            clock.resume(); // ya estaba en marcha
            clock.pause();
            clock.pause();
            fakeRealNanos.addAndGet(ONE_SECOND_NANOS);
            clock.resume();
            clock.resume();

            assertThat(clock.now()).isEqualTo(Duration.ZERO);
            assertThat(clock.snapshot().paused()).isFalse();
        }

        @Test
        void speed_can_be_changed_while_paused() {
            SimClock clock = clockAt(60);
            clock.pause();

            clock.setSpeed(300);
            clock.resume();
            fakeRealNanos.addAndGet(ONE_SECOND_NANOS);

            assertThat(clock.now()).isEqualTo(Duration.ofMinutes(5));
        }

        @Test
        void snapshot_reports_time_speed_and_pause_state() {
            SimClock clock = clockAt(60);
            fakeRealNanos.addAndGet(ONE_SECOND_NANOS);
            clock.pause();

            ClockSnapshot snapshot = clock.snapshot();

            assertThat(snapshot).isEqualTo(new ClockSnapshot(60_000, 60.0, true));
        }

        @Test
        void rejects_speeds_outside_the_valid_range() {
            SimClock clock = clockAt(60);

            for (double invalid : new double[] {0, -1, SimClock.MIN_SPEED / 2, SimClock.MAX_SPEED * 2,
                    Double.NaN, Double.POSITIVE_INFINITY}) {
                assertThatThrownBy(() -> clock.setSpeed(invalid))
                        .as("speed %s", invalid)
                        .isInstanceOf(IllegalArgumentException.class);
            }
            assertThat(clock.snapshot().speed()).isEqualTo(60.0);
        }
    }

    /** Sleep: bloquea hilos reales, así que usa el reloj de verdad con velocidades altas. */
    @Nested
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    class Sleeping {

        private final ExecutorService executor = Executors.newCachedThreadPool();

        @AfterEach
        void shutdownThreads() {
            executor.shutdownNow();
        }

        @Test
        void sleeps_the_requested_simulated_time() throws Exception {
            SimClock clock = new SimClock(System::nanoTime, 1000); // 1 s simulado = 1 ms real
            Duration before = clock.now();

            clock.sleep(Duration.ofSeconds(2));

            assertThat(clock.now().minus(before)).isGreaterThanOrEqualTo(Duration.ofSeconds(2));
        }

        @Test
        void non_positive_duration_returns_immediately() throws Exception {
            SimClock clock = new SimClock(System::nanoTime, 1);

            clock.sleep(Duration.ZERO);
            clock.sleep(Duration.ofSeconds(-5));

            assertThat(clock.now()).isLessThan(Duration.ofSeconds(1));
        }

        @Test
        void sleeper_stays_blocked_while_the_clock_is_paused_and_finishes_after_resume() throws Exception {
            SimClock clock = new SimClock(System::nanoTime, 1000);
            clock.pause();
            Future<?> sleeper = executor.submit(() -> {
                clock.sleep(Duration.ofSeconds(1)); // 1 ms real si corriera
                return null;
            });

            // Si corriera tardaría ~1 ms: que siga bloqueado tras 200 ms prueba que respeta la pausa.
            assertThatThrownBy(() -> sleeper.get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            clock.resume();
            sleeper.get(5, TimeUnit.SECONDS);
        }

        @Test
        void speeding_up_the_clock_shortens_a_sleep_already_in_progress() throws Exception {
            SimClock clock = new SimClock(System::nanoTime, 1); // 10 min simulados = 10 min reales
            Future<?> sleeper = executor.submit(() -> {
                clock.sleep(Duration.ofMinutes(10));
                return null;
            });

            clock.setSpeed(SimClock.MAX_SPEED); // ahora 10 min simulados = 6 ms reales

            sleeper.get(5, TimeUnit.SECONDS);
        }

        @Test
        void interrupting_a_sleeper_makes_sleep_throw_interrupted_exception() throws Exception {
            SimClock clock = new SimClock(System::nanoTime, 1); // dormiría 10 min reales
            AtomicBoolean interruptedExceptionRaised = new AtomicBoolean();
            Thread sleepingThread = Thread.ofPlatform().start(() -> {
                try {
                    clock.sleep(Duration.ofMinutes(10));
                } catch (InterruptedException e) {
                    interruptedExceptionRaised.set(true);
                }
            });

            sleepingThread.interrupt();
            sleepingThread.join(5_000);

            assertThat(sleepingThread.isAlive()).isFalse();
            assertThat(interruptedExceptionRaised).isTrue();
        }
    }
}
