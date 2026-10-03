package com.redxela.benchmark;

import com.redxela.catalog.ProductCatalog;
import com.redxela.customers.CustomerRegistry;
import com.redxela.domain.CompletedOrder;
import com.redxela.domain.ResourceType;
import com.redxela.inventory.Inventory;
import com.redxela.orders.OrderService;
import com.redxela.processing.OrderLifecycleListener;
import com.redxela.processing.OrderProcessor;
import com.redxela.processing.OrderScheduler;
import com.redxela.processing.OrderWorkflow;
import com.redxela.processing.ProcessingTimes;
import com.redxela.resources.PoolSnapshot;
import com.redxela.resources.ResourceRegistry;
import com.redxela.scheduling.SchedulingPolicy;
import com.redxela.sim.SimClock;
import com.redxela.stats.SimulationStatistics;
import com.redxela.stats.StatsSnapshot;
import java.time.Duration;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Corre <b>una política</b> con una traza de carga, en una simulación completamente aislada del sistema en vivo.
 *
 * <h3>Qué se aísla y qué se deja fuera</h3>
 * Cada corrida arma su propio reloj, pools de recursos, inventario, planificador, procesador y estadísticas, y
 * los desmonta al terminar: no toca el reloj, los pedidos ni el inventario del sistema en vivo. Se dejan fuera,
 * a propósito, lo que no depende de la política de atención y la ensuciaría:
 * <ul>
 *   <li><b>El stock:</b> el inventario es abundante, así que ningún pedido espera mercadería.</li>
 *   <li><b>Los camiones y el almacén:</b> no intervienen, así que tampoco puede haber deadlock.</li>
 * </ul>
 *
 * <h3>Reproducibilidad y su límite</h3>
 * La entrada es idéntica (la traza), pero es una simulación con hilos reales y no un motor de eventos
 * discretos: la planificación de hilos del sistema operativo mueve unos instantes simulados. Por eso corre a
 * una velocidad en que ese ruido (décimas de segundo simulado) es mínimo frente a las esperas que se miden
 * (minutos), y las diferencias entre políticas son mucho mayores que la variación de una misma política
 * consigo misma.
 */
public final class PolicyRun {

    /** Velocidad del reloj de la corrida: 1 s real = 10 min simulados. */
    static final double SPEED = 600.0;
    /** Unidades de cada producto: tantas que ningún pedido de la traza puede quedarse sin stock. */
    private static final int ABUNDANT_STOCK = 1_000_000;
    private static final long POLL_MILLIS = 20;
    private static final Duration MAX_REAL_TIME = Duration.ofMinutes(5);

    private PolicyRun() {
    }

    /** Ejecutor real que usa la aplicación. */
    public static PolicyExecutor executor() {
        return PolicyRun::execute;
    }

    static PolicyResult execute(SchedulingPolicy policy, List<TraceEntry> trace, IntConsumer onProgress)
            throws InterruptedException {
        SimClock clock = new SimClock();
        clock.setSpeed(SPEED);
        ResourceRegistry resources = new ResourceRegistry(() -> clock.now().toNanos());
        ProductCatalog catalog = ProductCatalog.defaultCatalog();
        Inventory inventory = new Inventory(catalog, ABUNDANT_STOCK);
        OrderWorkflow workflow = new OrderWorkflow(resources, clock);
        OrderProcessor processor = new OrderProcessor(workflow, ProcessingTimes.defaults());
        OrderScheduler scheduler = new OrderScheduler(clock, resources, workflow, processor, inventory);
        SimulationStatistics statistics = new SimulationStatistics(clock, resources);
        OrderService orders = new OrderService(catalog, CustomerRegistry.withSampleCustomers(), scheduler);

        processor.addListener(new SimulationListener(statistics, inventory));
        scheduler.setPolicy(policy);
        scheduler.start();
        try {
            feed(trace, clock, orders);
            awaitAllDispatched(trace.size(), processor, onProgress);
            return collect(policy, clock, resources, statistics);
        } finally {
            // Se desmonta siempre, también si se cancela: no deben quedar hilos de una corrida abandonada.
            scheduler.close();
            processor.close();
        }
    }

    /** Hace llegar cada pedido en su instante simulado exacto. */
    private static void feed(List<TraceEntry> trace, SimClock clock, OrderService orders) throws InterruptedException {
        for (TraceEntry entry : trace) {
            clock.sleep(entry.arrivalAt().minus(clock.now()));
            orders.place(entry.spec().customerId(), entry.spec().lines());
        }
    }

    private static void awaitAllDispatched(int expected, OrderProcessor processor, IntConsumer onProgress)
            throws InterruptedException {
        long deadline = System.nanoTime() + MAX_REAL_TIME.toNanos();
        long completed;
        while ((completed = processor.completedCount()) < expected) {
            onProgress.accept((int) completed);
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException(
                        "The run did not finish within " + MAX_REAL_TIME.toMinutes() + " minutes (" + completed + "/" + expected + ")");
            }
            Thread.sleep(POLL_MILLIS);
        }
        onProgress.accept(expected);
    }

    private static PolicyResult collect(SchedulingPolicy policy, SimClock clock, ResourceRegistry resources,
                                        SimulationStatistics statistics) {
        Duration finishedAt = clock.now();
        StatsSnapshot stats = statistics.snapshot(finishedAt);
        double workerUtilization = resources.snapshots().stream()
                .filter(pool -> pool.type() == ResourceType.WAREHOUSE_WORKER)
                .mapToDouble(PoolSnapshot::utilization)
                .findFirst()
                .orElse(0.0);
        return new PolicyResult(policy, stats, finishedAt.toMillis(), workerUtilization);
    }

    /** Alimenta las estadísticas con cada pedido despachado y lleva la cuenta del stock (que no importa, pero debe cuadrar). */
    private static final class SimulationListener implements OrderLifecycleListener {
        private final SimulationStatistics statistics;
        private final Inventory inventory;

        private SimulationListener(SimulationStatistics statistics, Inventory inventory) {
            this.statistics = statistics;
            this.inventory = inventory;
        }

        @Override
        public void onCompleted(CompletedOrder order) {
            statistics.record(order);
            inventory.consume(order.orderId());
        }

        @Override
        public void onAborted(String orderId) {
            inventory.release(orderId);
        }
    }
}
