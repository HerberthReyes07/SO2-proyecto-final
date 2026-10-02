package com.redxela;

import com.redxela.api.ApiRoutes;
import com.redxela.api.ApiServer;
import com.redxela.api.CatalogRoutes;
import com.redxela.api.ClockRoutes;
import com.redxela.api.CustomerRoutes;
import com.redxela.api.GeneratorRoutes;
import com.redxela.api.OrderRoutes;
import com.redxela.api.SchedulingRoutes;
import com.redxela.api.StatsRoutes;
import com.redxela.api.SimulationSnapshotFactory;
import com.redxela.api.SnapshotBroadcaster;
import com.redxela.catalog.ProductCatalog;
import com.redxela.customers.CustomerRegistry;
import com.redxela.generator.OrderGenerator;
import com.redxela.generator.WorkloadProfile;
import com.redxela.orders.OrderService;
import com.redxela.processing.OrderProcessor;
import com.redxela.processing.OrderScheduler;
import com.redxela.processing.OrderWorkflow;
import com.redxela.processing.ProcessingTimes;
import com.redxela.resources.ResourceRegistry;
import com.redxela.sim.SimClock;
import com.redxela.stats.SimulationStatistics;
import java.time.Duration;
import java.util.List;

/**
 * Raíz de composición: crea todos los componentes y los conecta entre sí. Es el único lugar que
 * conoce a todos; el resto de las clases solo reciben lo que necesitan por su constructor.
 * Los tests de integración usan esta misma clase, así prueban el cableado real.
 */
public final class ApplicationComponents implements AutoCloseable {

    private static final Duration SNAPSHOT_PERIOD = Duration.ofMillis(300);

    private final SimClock clock = new SimClock();
    private final OrderProcessor orderProcessor;
    private final OrderScheduler scheduler;
    private final OrderGenerator generator;
    private final SnapshotBroadcaster broadcaster;
    private final ApiServer server;

    public ApplicationComponents() {
        // La ocupación se mide con el reloj simulado: una pausa no debe contar como tiempo ocioso.
        ResourceRegistry resources = new ResourceRegistry(() -> clock.now().toNanos());
        ProductCatalog catalog = ProductCatalog.defaultCatalog();
        CustomerRegistry customers = CustomerRegistry.withSampleCustomers();

        OrderWorkflow workflow = new OrderWorkflow(resources, clock);
        this.orderProcessor = new OrderProcessor(workflow, ProcessingTimes.defaults());
        this.scheduler = new OrderScheduler(clock, resources, workflow, orderProcessor);
        OrderService orderService = new OrderService(catalog, customers, scheduler);

        SimulationStatistics statistics = new SimulationStatistics(clock, resources);
        orderProcessor.addCompletionListener(statistics::record);
        this.generator = new OrderGenerator(
                clock, WorkloadProfile.defaults(), catalog, customers::all, orderService::place);

        SimulationSnapshotFactory snapshots = new SimulationSnapshotFactory(
                clock, resources, scheduler, orderProcessor, generator, statistics);
        this.broadcaster = new SnapshotBroadcaster(snapshots::create);
        List<ApiRoutes> routes = List.of(
                new ClockRoutes(clock),
                new CatalogRoutes(catalog),
                new CustomerRoutes(customers),
                new OrderRoutes(orderService),
                new SchedulingRoutes(scheduler),
                new GeneratorRoutes(generator),
                new StatsRoutes(statistics));
        this.server = new ApiServer(broadcaster, routes);
    }

    /** Arranca el servidor y devuelve el puerto real (útil con el puerto 0 en los tests). */
    public int start(int port) {
        scheduler.start();
        generator.start();
        broadcaster.start(SNAPSHOT_PERIOD);
        return server.start(port);
    }

    public SimClock clock() {
        return clock;
    }

    @Override
    public void close() {
        broadcaster.close();
        server.close();
        generator.close(); // el generador deja de crear pedidos antes de apagar lo demás
        scheduler.close(); // primero el despachador, para que no admita pedidos en un procesador cerrado
        orderProcessor.close();
    }
}
