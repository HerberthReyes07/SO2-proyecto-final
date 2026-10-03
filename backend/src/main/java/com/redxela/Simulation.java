package com.redxela;

import com.redxela.api.ApiRoutes;
import com.redxela.api.BenchmarkRoutes;
import com.redxela.api.CatalogRoutes;
import com.redxela.api.ClockRoutes;
import com.redxela.api.CustomerRoutes;
import com.redxela.api.DeadlockRoutes;
import com.redxela.api.GeneratorRoutes;
import com.redxela.api.OrderRoutes;
import com.redxela.api.ReceptionRoutes;
import com.redxela.api.SchedulingRoutes;
import com.redxela.api.SimulationRoutes;
import com.redxela.api.SimulationSnapshotFactory;
import com.redxela.api.SnapshotBroadcaster;
import com.redxela.api.StatsRoutes;
import com.redxela.api.WarehouseRoutes;
import com.redxela.catalog.ProductCatalog;
import com.redxela.benchmark.BenchmarkRunner;
import com.redxela.benchmark.PolicyRun;
import com.redxela.deadlock.AcquisitionPolicy;
import com.redxela.deadlock.DeadlockDetector;
import com.redxela.demo.DeadlockScenario;
import com.redxela.customers.CustomerRegistry;
import com.redxela.generator.OrderGenerator;
import com.redxela.generator.WorkloadProfile;
import com.redxela.inventory.Inventory;
import com.redxela.inventory.InventoryTimes;
import com.redxela.inventory.InventoryWorker;
import com.redxela.inventory.ReceptionBuffer;
import com.redxela.orders.OrderService;
import com.redxela.processing.OrderLifecycleListener;
import com.redxela.processing.OrderProcessor;
import com.redxela.processing.OrderScheduler;
import com.redxela.processing.OrderWorkflow;
import com.redxela.processing.ProcessingTimes;
import com.redxela.processing.StockHandling;
import com.redxela.reception.ReceptionService;
import com.redxela.reception.ReceptionTimes;
import com.redxela.resources.ResourceRegistry;
import com.redxela.sim.SimClock;
import com.redxela.stats.SimulationStatistics;
import com.redxela.warehouse.PlacementStrategy;
import com.redxela.warehouse.StoreResult;
import com.redxela.warehouse.Warehouse;
import com.redxela.warehouse.WarehouseLayout;
import java.time.Duration;
import java.util.List;

/**
 * Todo el estado y los hilos de una simulación: reloj, recursos, planificador, inventario, almacén, detector de
 * deadlock, etc., ya conectados entre sí. No incluye el servidor HTTP.
 *
 * <p>Existe como clase aparte para que reiniciar la simulación sea descartar este objeto entero y construir
 * otro: es más seguro que limpiar uno por uno decenas de estados con hilos en vuelo (pedidos a mitad de una
 * etapa, camiones esperando recursos, un deadlock vigente), donde cada limpieza invita a una carrera.
 */
final class Simulation implements AutoCloseable {

    private static final Duration SNAPSHOT_PERIOD = Duration.ofMillis(300);

    /**
     * Existencias iniciales de cada producto. Con la carga por defecto alcanzan para unas horas
     * simuladas; después los pedidos esperan stock hasta que se registre una llegada de mercadería.
     * Con el almacén por defecto ocupan 112 de las 240 ubicaciones.
     */
    private static final int INITIAL_UNITS_PER_PRODUCT = 300;
    /** Cada cuánto el detector revisa si hay deadlock (tiempo real: un deadlock no depende del reloj simulado). */
    private static final Duration DEADLOCK_SCAN_PERIOD = Duration.ofMillis(500);
    /** Mensajes que caben entre Recepción e Inventario antes de que Recepción deba esperar. */
    private static final int RECEPTION_BUFFER_CAPACITY = 10;

    private final SimClock clock = new SimClock();
    private final OrderProcessor orderProcessor;
    private final OrderScheduler scheduler;
    private final OrderGenerator generator;
    private final ReceptionService reception;
    private final InventoryWorker inventoryWorker;
    private final DeadlockDetector deadlockDetector;
    private final BenchmarkRunner benchmark;
    private final SnapshotBroadcaster broadcaster;
    private final List<ApiRoutes> routes;

    /**
     * @param customers    cuentas de cliente; se reciben de fuera para que sobrevivan a un reinicio
     * @param resetRequest qué hacer cuando el usuario pide reiniciar la simulación
     */
    Simulation(CustomerRegistry customers, Runnable resetRequest) {
        // La ocupación se mide con el reloj simulado: una pausa no debe contar como tiempo ocioso.
        ResourceRegistry resources = new ResourceRegistry(() -> clock.now().toNanos());
        ProductCatalog catalog = ProductCatalog.defaultCatalog();
        Inventory inventory = new Inventory(catalog, INITIAL_UNITS_PER_PRODUCT);
        Warehouse warehouse = new Warehouse(WarehouseLayout.defaults(), catalog, PlacementStrategy.FIRST_FREE);
        storeInitialStock(warehouse, catalog);

        OrderWorkflow workflow = new OrderWorkflow(resources, clock);
        this.orderProcessor = new OrderProcessor(workflow, ProcessingTimes.defaults());
        this.scheduler = new OrderScheduler(clock, resources, workflow, orderProcessor, inventory);
        OrderService orderService = new OrderService(catalog, customers, scheduler);

        SimulationStatistics statistics = new SimulationStatistics(clock, resources);
        orderProcessor.addListener(OrderLifecycleListener.onCompleted(statistics::record));
        orderProcessor.addListener(new StockHandling(inventory, warehouse));
        orderProcessor.onExpelled(scheduler::requeue); // un pedido expulsado por un deadlock vuelve a la lista de espera
        this.generator = new OrderGenerator(
                clock, WorkloadProfile.defaults(), catalog, customers::all, orderService::place);

        // Recepción e Inventario solo se comunican por este buffer; cuando Inventario suma stock,
        // el planificador reevalúa los pedidos que lo esperaban.
        ReceptionBuffer receptionBuffer = new ReceptionBuffer(RECEPTION_BUFFER_CAPACITY);
        AcquisitionPolicy acquisitionPolicy = new AcquisitionPolicy();
        this.deadlockDetector = new DeadlockDetector(resources, clock, acquisitionPolicy, DEADLOCK_SCAN_PERIOD);
        this.reception = new ReceptionService(
                resources, clock, receptionBuffer, catalog, acquisitionPolicy, ReceptionTimes.defaults());
        this.inventoryWorker = new InventoryWorker(
                receptionBuffer, inventory, warehouse, clock, InventoryTimes.defaults(),
                productId -> scheduler.stockChanged());

        DeadlockScenario deadlockScenario = new DeadlockScenario(clock, acquisitionPolicy, orderService, reception,
                inventory, customers, ProcessingTimes.defaults(), ReceptionTimes.defaults(),
                () -> isIdle(clock, orderProcessor, scheduler, reception, generator),
                () -> workFinished(clock, orderProcessor, scheduler, reception),
                () -> deadlockDetector.snapshot().detected());
        this.benchmark = new BenchmarkRunner(PolicyRun.executor());
        SimulationSnapshotFactory snapshots = new SimulationSnapshotFactory(clock, resources, scheduler,
                orderProcessor, generator, statistics, inventory, receptionBuffer, inventoryWorker, reception, warehouse,
                deadlockDetector, deadlockScenario, benchmark);
        this.broadcaster = new SnapshotBroadcaster(snapshots::create);
        this.routes = List.of(
                new ClockRoutes(clock),
                new CatalogRoutes(catalog),
                new CustomerRoutes(customers),
                new OrderRoutes(orderService),
                new SchedulingRoutes(scheduler),
                new GeneratorRoutes(generator),
                new StatsRoutes(statistics),
                new ReceptionRoutes(reception),
                new WarehouseRoutes(warehouse),
                new DeadlockRoutes(deadlockDetector, acquisitionPolicy, deadlockScenario),
                new BenchmarkRoutes(benchmark),
                new SimulationRoutes(resetRequest));
    }

    /** El sistema está en reposo: sin pedidos en proceso ni en espera, sin camiones y con el generador detenido. */
    private static boolean isIdle(SimClock clock, OrderProcessor processor, OrderScheduler scheduler,
                                  ReceptionService reception, OrderGenerator generator) {
        return workFinished(clock, processor, scheduler, reception) && !generator.snapshot().enabled();
    }

    /** No quedan pedidos en proceso ni en espera ni camiones en Recepción (el generador puede seguir encendido). */
    private static boolean workFinished(SimClock clock, OrderProcessor processor, OrderScheduler scheduler,
                                        ReceptionService reception) {
        Duration now = clock.now();
        return processor.activeOrders(now).isEmpty()
                && scheduler.snapshot(now).waitingTotal() == 0
                && reception.snapshot(now).trucks().isEmpty();
    }

    /**
     * Guarda en el almacén las existencias iniciales (un lote por producto), de modo que lo que dice el
     * inventario coincida con lo que hay físicamente.
     */
    private static void storeInitialStock(Warehouse warehouse, ProductCatalog catalog) {
        catalog.all().forEach(product -> {
            StoreResult result = warehouse.store("INIT-" + product.id(), product.id(), INITIAL_UNITS_PER_PRODUCT);
            if (result.rejected() > 0) {
                throw new IllegalStateException("The warehouse is too small for the initial stock of " + product.id());
            }
        });
    }

    /** Arranca los hilos propios de la simulación y el envío periódico del estado. */
    void start() {
        inventoryWorker.start();
        deadlockDetector.start();
        scheduler.start();
        generator.start();
        broadcaster.start(SNAPSHOT_PERIOD);
    }

    List<ApiRoutes> routes() {
        return routes;
    }

    SnapshotBroadcaster broadcaster() {
        return broadcaster;
    }

    SimClock clock() {
        return clock;
    }

    /** Deja de enviar el estado a los clientes; se hace antes de cerrar el servidor para no escribir en sesiones que se cierran. */
    void stopPublishing() {
        broadcaster.close();
    }

    @Override
    public void close() {
        stopPublishing();
        benchmark.close(); // una comparación en marcha se cancela: usa hilos propios y no debe sobrevivir al sistema
        generator.close(); // el generador deja de crear pedidos antes de apagar lo demás
        scheduler.close(); // primero el despachador, para que no admita pedidos en un procesador cerrado
        orderProcessor.close();
        reception.close();
        inventoryWorker.close();
        deadlockDetector.close();
    }
}
