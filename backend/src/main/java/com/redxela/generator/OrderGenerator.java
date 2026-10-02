package com.redxela.generator;

import com.redxela.catalog.ProductCatalog;
import com.redxela.domain.Customer;
import com.redxela.sim.SimClock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generador automático de pedidos: simula la llegada continua de clientes de todos los niveles.
 *
 * <h3>Llegadas de Poisson con intensidad ajustable</h3>
 * El tiempo entre pedidos es exponencial. Para que un cambio de intensidad se note de inmediato
 * (y no tras esperar un intervalo ya sorteado, que con intensidad baja puede ser de horas
 * simuladas), la espera se hace en tajadas cortas de tiempo simulado: cada una consume del
 * "trabajo" sorteado a la intensidad vigente en ese momento (<i>time-rescaling</i> de un proceso
 * de Poisson no homogéneo).
 *
 * <h3>Sincronización</h3>
 * Un {@link ReentrantLock} protege la configuración (habilitado, intensidad, semilla). El hilo
 * generador duerme en una {@link Condition} mientras está deshabilitado y se despierta al
 * reconfigurarlo; las esperas de tiempo usan el reloj simulado, así que respetan la pausa.
 */
public final class OrderGenerator implements AutoCloseable {

    public static final double MIN_ORDERS_PER_MINUTE = 0.05;
    public static final double MAX_ORDERS_PER_MINUTE = 20.0;
    public static final double DEFAULT_ORDERS_PER_MINUTE = 1.0;

    private static final Logger LOG = LoggerFactory.getLogger(OrderGenerator.class);
    private static final Duration WAIT_SLICE = Duration.ofSeconds(10);
    private static final double MILLIS_PER_MINUTE = 60_000.0;
    private static final long SHUTDOWN_WAIT_MILLIS = 1_000;
    // Las semillas por defecto son pequeñas a propósito: la interfaz (JavaScript) solo representa sin
    // pérdida enteros hasta 2^53, y una semilla que se muestra redondeada no sirve para repetir un experimento.
    private static final long DEFAULT_SEED_BOUND = 1_000_000_000L;

    private final SimClock clock;
    private final WorkloadProfile profile;
    private final ProductCatalog catalog;
    private final Supplier<List<Customer>> customers;
    private final OrderPlacer placer;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition configChanged = lock.newCondition();
    private final AtomicLong generated = new AtomicLong();
    private Thread thread;

    // Configuración protegida por `lock`.
    private boolean enabled;
    private double ordersPerMinute = DEFAULT_ORDERS_PER_MINUTE;
    private long seed;
    /** Cambia cada vez que se pide una semilla nueva; el hilo lo compara para saber si debe reiniciar su secuencia. */
    private long seedVersion;

    public OrderGenerator(SimClock clock, WorkloadProfile profile, ProductCatalog catalog,
                          Supplier<List<Customer>> customers, OrderPlacer placer) {
        this.clock = clock;
        this.profile = profile;
        this.catalog = catalog;
        this.customers = customers;
        this.placer = placer;
        this.seed = ThreadLocalRandom.current().nextLong(DEFAULT_SEED_BOUND);
    }

    /** Arranca el hilo generador (queda dormido hasta que se habilite). */
    public synchronized void start() {
        if (thread == null) {
            thread = Thread.ofPlatform().name("order-generator").start(this::run);
        }
    }

    /**
     * Cambia la configuración; los parámetros nulos no se modifican. Una semilla nueva reinicia la
     * secuencia de pedidos desde su principio.
     *
     * @throws IllegalArgumentException si la intensidad está fuera de rango
     */
    public GeneratorSnapshot configure(Boolean enable, Double newOrdersPerMinute, Long newSeed) {
        if (newOrdersPerMinute != null && !isValidRate(newOrdersPerMinute)) {
            throw new IllegalArgumentException("ordersPerMinute must be between " + MIN_ORDERS_PER_MINUTE
                    + " and " + MAX_ORDERS_PER_MINUTE + " but was " + newOrdersPerMinute);
        }
        lock.lock();
        try {
            if (newOrdersPerMinute != null) {
                ordersPerMinute = newOrdersPerMinute;
            }
            if (newSeed != null) {
                seed = newSeed;
                seedVersion++;
            }
            if (enable != null) {
                enabled = enable;
            }
            configChanged.signalAll();
            return snapshotLocked();
        } finally {
            lock.unlock();
        }
    }

    public GeneratorSnapshot snapshot() {
        lock.lock();
        try {
            return snapshotLocked();
        } finally {
            lock.unlock();
        }
    }

    private static boolean isValidRate(double rate) {
        return Double.isFinite(rate) && rate >= MIN_ORDERS_PER_MINUTE && rate <= MAX_ORDERS_PER_MINUTE;
    }

    private GeneratorSnapshot snapshotLocked() {
        return new GeneratorSnapshot(enabled, ordersPerMinute, seed, generated.get());
    }

    // ---- Hilo generador ----

    private void run() {
        try {
            WorkloadGenerator workload = null;
            long appliedSeedVersion = -1;
            while (!Thread.currentThread().isInterrupted()) {
                Config config = awaitEnabled();
                if (workload == null || config.seedVersion() != appliedSeedVersion) {
                    workload = new WorkloadGenerator(profile, catalog, config.seed());
                    appliedSeedVersion = config.seedVersion();
                }
                if (waitForNextArrival(workload.nextArrivalWork(), appliedSeedVersion)) {
                    place(workload);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.info("Order generator stopped");
        }
    }

    /** Duerme mientras el generador está deshabilitado y devuelve la configuración vigente al habilitarse. */
    private Config awaitEnabled() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (!enabled) {
                configChanged.await();
            }
            return readConfig();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Espera, en tajadas de tiempo simulado, a que se consuma el trabajo sorteado.
     *
     * @return {@code true} si llegó el momento de generar el pedido; {@code false} si mientras tanto
     *         se deshabilitó o se cambió la semilla (la llegada pendiente se descarta)
     */
    private boolean waitForNextArrival(double work, long appliedSeedVersion) throws InterruptedException {
        double remainingWork = work;
        while (remainingWork > 0) {
            Config config = currentConfig();
            if (!config.enabled() || config.seedVersion() != appliedSeedVersion) {
                return false;
            }
            long neededMillis = (long) Math.ceil(remainingWork / config.ordersPerMinute() * MILLIS_PER_MINUTE);
            long sliceMillis = Math.max(1, Math.min(WAIT_SLICE.toMillis(), neededMillis));
            clock.sleep(Duration.ofMillis(sliceMillis));
            remainingWork -= (sliceMillis / MILLIS_PER_MINUTE) * config.ordersPerMinute();
        }
        return currentConfig().enabled();
    }

    private void place(WorkloadGenerator workload) {
        try {
            WorkloadGenerator.OrderSpec spec = workload.nextOrder(customers.get());
            placer.place(spec.customerId(), spec.lines());
            generated.incrementAndGet();
        } catch (RuntimeException e) {
            // Un pedido que no se pudo crear no debe detener al generador.
            LOG.warn("Generated order could not be placed: {}", e.getMessage());
        }
    }

    private Config currentConfig() {
        lock.lock();
        try {
            return readConfig();
        } finally {
            lock.unlock();
        }
    }

    private Config readConfig() {
        return new Config(enabled, ordersPerMinute, seed, seedVersion);
    }

    private record Config(boolean enabled, double ordersPerMinute, long seed, long seedVersion) {
    }

    /** Detiene el hilo generador. */
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
