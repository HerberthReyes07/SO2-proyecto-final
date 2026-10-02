package com.redxela.sim;

import java.time.Duration;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

/**
 * Reloj de la simulación: convierte tiempo real en tiempo simulado con un factor de aceleración y
 * permite pausarlo.
 *
 * <p>Los tiempos objetivo del enunciado (10, 30, 60, 120 minutos) serían inviables en tiempo real,
 * por eso toda la simulación consulta este reloj y ningún componente usa el reloj del sistema para
 * decidir nada.
 *
 * <h3>Modelo</h3>
 * El tiempo simulado no se almacena avanzando: se <b>calcula</b> a partir de un punto de anclaje
 * ({@code baseSimNanos}, {@code baseRealNanos}):
 * <pre>sim = baseSim + (realAhora - baseReal) × velocidad</pre>
 * Cada cambio (velocidad, pausa) re-ancla el reloj, de modo que el tiempo ya transcurrido nunca
 * cambia retroactivamente. Así no hace falta un hilo que "dé cuerda" al reloj.
 *
 * <h3>Sincronización</h3>
 * Un {@link ReentrantLock} protege todo el estado. Los hilos que duermen en {@link #sleep} esperan
 * en una {@link Condition} y se despiertan cuando cambia la velocidad o la pausa para recalcular
 * cuánto les falta; esto es lo que hace que un {@code sleep} largo termine pronto si se acelera el reloj.
 */
public final class SimClock {

    public static final double DEFAULT_SPEED = 60.0;
    public static final double MIN_SPEED = 0.1;
    public static final double MAX_SPEED = 100_000.0;

    private final LongSupplier nanoSource;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();

    // Estado protegido por `lock`.
    private long baseSimNanos;
    private long baseRealNanos;
    private double speed;
    private boolean paused;

    /** Reloj que arranca en cero, en marcha, a la velocidad por defecto. */
    public SimClock() {
        this(System::nanoTime, DEFAULT_SPEED);
    }

    /**
     * @param nanoSource fuente de tiempo real en nanosegundos; se inyecta para poder simular el
     *                   paso del tiempo de forma determinista en los tests
     */
    SimClock(LongSupplier nanoSource, double initialSpeed) {
        validateSpeed(initialSpeed);
        this.nanoSource = nanoSource;
        this.speed = initialSpeed;
        this.baseRealNanos = nanoSource.getAsLong();
    }

    /** Tiempo simulado transcurrido desde el inicio de la simulación. */
    public Duration now() {
        lock.lock();
        try {
            return Duration.ofNanos(currentSimNanos());
        } finally {
            lock.unlock();
        }
    }

    public ClockSnapshot snapshot() {
        lock.lock();
        try {
            return new ClockSnapshot(Duration.ofNanos(currentSimNanos()).toMillis(), speed, paused);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Cambia el factor de aceleración. El tiempo simulado ya transcurrido se conserva.
     *
     * @throws IllegalArgumentException si la velocidad no es finita o está fuera de
     *                                  [{@link #MIN_SPEED}, {@link #MAX_SPEED}]
     */
    public void setSpeed(double newSpeed) {
        validateSpeed(newSpeed);
        lock.lock();
        try {
            reanchor();
            speed = newSpeed;
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /** Detiene el tiempo simulado. No hace nada si ya estaba en pausa. */
    public void pause() {
        lock.lock();
        try {
            if (!paused) {
                reanchor(); // congela el valor actual antes de marcar la pausa
                paused = true;
                changed.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    /** Reanuda el tiempo simulado desde donde se detuvo. No hace nada si ya estaba en marcha. */
    public void resume() {
        lock.lock();
        try {
            if (paused) {
                // Solo se mueve el ancla real: el tiempo en pausa no debe contar como simulado.
                baseRealNanos = nanoSource.getAsLong();
                paused = false;
                changed.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Bloquea al hilo llamador durante {@code simulatedDuration} de tiempo <b>simulado</b>.
     *
     * <p>Si el reloj está en pausa, el hilo espera sin consumir tiempo; si cambia la velocidad,
     * recalcula cuánto le falta. Una duración nula o negativa retorna de inmediato.
     *
     * @throws InterruptedException si el hilo es interrumpido mientras duerme
     */
    public void sleep(Duration simulatedDuration) throws InterruptedException {
        if (simulatedDuration.isNegative() || simulatedDuration.isZero()) {
            return;
        }
        lock.lockInterruptibly();
        try {
            long targetSimNanos = currentSimNanos() + simulatedDuration.toNanos();
            long remaining = targetSimNanos - currentSimNanos();
            while (remaining > 0) {
                if (paused) {
                    changed.await(); // sin plazo: lo despierta resume() o un cambio de velocidad
                } else {
                    changed.awaitNanos(toRealNanos(remaining));
                }
                remaining = targetSimNanos - currentSimNanos();
            }
        } finally {
            lock.unlock();
        }
    }

    // ---- Internos: todos se invocan con `lock` tomado ----

    private long currentSimNanos() {
        if (paused) {
            return baseSimNanos;
        }
        long realElapsed = nanoSource.getAsLong() - baseRealNanos;
        return baseSimNanos + (long) (realElapsed * speed);
    }

    /** Fija el ancla en el instante actual para que un cambio de estado no altere el pasado. */
    private void reanchor() {
        baseSimNanos = currentSimNanos();
        baseRealNanos = nanoSource.getAsLong();
    }

    /** Tiempo real que tarda en transcurrir {@code simNanos} a la velocidad actual (redondeado hacia arriba). */
    private long toRealNanos(long simNanos) {
        return Math.max(1L, (long) Math.ceil(simNanos / speed));
    }

    private static void validateSpeed(double candidate) {
        boolean valid = Double.isFinite(candidate) && candidate >= MIN_SPEED && candidate <= MAX_SPEED;
        if (!valid) {
            throw new IllegalArgumentException(
                    "speed must be between " + MIN_SPEED + " and " + MAX_SPEED + " but was " + candidate);
        }
    }
}
