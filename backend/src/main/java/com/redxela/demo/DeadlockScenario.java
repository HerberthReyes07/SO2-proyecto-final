package com.redxela.demo;

import com.redxela.customers.CustomerRegistry;
import com.redxela.deadlock.AcquisitionPolicy;
import com.redxela.deadlock.PreventionMode;
import com.redxela.deadlock.StateConflictException;
import com.redxela.domain.Customer;
import com.redxela.domain.ResourceType;
import com.redxela.domain.ServiceLevel;
import com.redxela.domain.Stage;
import com.redxela.inventory.Inventory;
import com.redxela.orders.OrderLineRequest;
import com.redxela.orders.OrderService;
import com.redxela.processing.ProcessingTimes;
import com.redxela.reception.ReceptionService;
import com.redxela.reception.ReceptionTimes;
import com.redxela.sim.SimClock;
import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Escenario de demostración: provoca a propósito la situación de deadlock entre Recepción y
 * Despacho, o la misma situación con la prevención activa para ver que no ocurre.
 *
 * <h3>La coreografía</h3>
 * Un deadlock aquí necesita tres operaciones: dos camiones, que toman las dos áreas de carga y
 * todavía no han pedido el escáner, y un pedido que justo entonces ya tiene el escáner y espera un
 * área de carga. Para que coincidan:
 * <ol>
 *   <li>Se registra un pedido de un producto perecedero (pasa por picking e inspección, sin empaque), que
 *       llegará a despacho en un tiempo conocido: {@code T = picking + inspección}.</li>
 *   <li>Se espera {@code T − maniobra/2} y se registran los dos camiones. Toman las áreas de carga y pasan
 *       {@code maniobra} acomodándose, de modo que piden el escáner <i>después</i> de que el pedido lo tomó.</li>
 *   <li>Con el orden natural (camión: andén → escáner; pedido: escáner → andén) queda una espera circular. Con
 *       el orden global, los camiones piden primero el escáner y el pedido espera su turno: no hay deadlock.</li>
 * </ol>
 * Todos los tiempos son simulados, así que el escenario funciona igual a cualquier velocidad.
 *
 * <h3>Informa cómo va y cómo terminó</h3>
 * A velocidad normal todo ocurre en un par de segundos, y con la prevención activa no pasa nada
 * "visible". Por eso el escenario sigue el trabajo hasta que termina y publica su {@link #snapshot()}:
 * en marcha, terminado sin deadlock o terminado habiéndolo tenido.
 */
public final class DeadlockScenario {

    private static final Logger LOG = LoggerFactory.getLogger(DeadlockScenario.class);
    /** Cada cuánto (tiempo real) se mira si el trabajo del escenario ya terminó. */
    private static final long FINISH_POLL_MILLIS = 100;

    /** Producto perecedero: su receta es control de calidad → escáner → área de carga, sin empaque. */
    static final String ORDER_PRODUCT = "P-010";
    static final int ORDER_UNITS = 1;
    static final String TRUCK_PRODUCT = "P-001";
    static final int TRUCK_UNITS = 5;
    /** Un camión por cada área de carga: entre todos las ocupan. */
    static final int TRUCKS = ResourceType.LOADING_DOCK.capacity();

    private final SimClock clock;
    private final AcquisitionPolicy policy;
    private final OrderService orders;
    private final ReceptionService reception;
    private final Inventory inventory;
    private final CustomerRegistry customers;
    private final ProcessingTimes processingTimes;
    private final ReceptionTimes receptionTimes;
    private final BooleanSupplier systemIsIdle;
    private final BooleanSupplier workFinished;
    private final LongSupplier deadlocksDetected;
    private volatile ScenarioSnapshot status = new ScenarioSnapshot(ScenarioPhase.IDLE, null);

    /**
     * @param systemIsIdle      indica que no hay pedidos, camiones ni generador en marcha; el escenario solo
     *                          sale como se espera si parte de un sistema en reposo
     * @param workFinished      indica que ya no quedan pedidos ni camiones en curso (aunque haya generador);
     *                          es lo que marca el final del escenario
     * @param deadlocksDetected cantidad de deadlocks confirmados hasta ahora; si sube mientras corre el
     *                          escenario, este terminó habiendo tenido uno
     */
    public DeadlockScenario(SimClock clock, AcquisitionPolicy policy, OrderService orders, ReceptionService reception,
                            Inventory inventory, CustomerRegistry customers, ProcessingTimes processingTimes,
                            ReceptionTimes receptionTimes, BooleanSupplier systemIsIdle,
                            BooleanSupplier workFinished, LongSupplier deadlocksDetected) {
        this.clock = clock;
        this.policy = policy;
        this.orders = orders;
        this.reception = reception;
        this.inventory = inventory;
        this.customers = customers;
        this.processingTimes = processingTimes;
        this.receptionTimes = receptionTimes;
        this.systemIsIdle = systemIsIdle;
        this.workFinished = workFinished;
        this.deadlocksDetected = deadlocksDetected;
    }

    /**
     * Pone la estrategia de prevención indicada y arranca la coreografía.
     *
     * @throws StateConflictException si hay otro escenario en curso, el sistema no está en reposo o falta stock
     */
    public synchronized void start(PreventionMode mode) {
        if (status.phase() == ScenarioPhase.RUNNING) {
            throw new StateConflictException("A scenario is already running");
        }
        if (!systemIsIdle.getAsBoolean()) {
            throw new StateConflictException(
                    "The scenario needs an idle system: stop the generator and wait for the orders and trucks in progress");
        }
        Customer customer = pickCustomer();
        List<OrderLineRequest> lines = List.of(new OrderLineRequest(ORDER_PRODUCT, ORDER_UNITS));
        if (inventory.snapshot().stream().noneMatch(stock -> stock.productId().equals(ORDER_PRODUCT) && stock.available() >= ORDER_UNITS)) {
            throw new StateConflictException("The scenario needs stock of " + ORDER_PRODUCT);
        }

        policy.setMode(mode);
        long deadlocksBefore = deadlocksDetected.getAsLong();
        orders.place(customer.id(), lines);
        status = new ScenarioSnapshot(ScenarioPhase.RUNNING, mode);
        Duration delay = trucksDelay();
        Thread.ofPlatform().name("deadlock-scenario").start(() -> run(mode, delay, deadlocksBefore));
    }

    /** El estado del último escenario lanzado. */
    public ScenarioSnapshot snapshot() {
        return status;
    }

    /** Cuánto esperar para registrar los camiones: el pedido llega a despacho en T, y los camiones deben pedir el escáner pasado T. */
    Duration trucksDelay() {
        Duration timeToDispatch = processingTimes.perUnit(Stage.PICKING).multipliedBy(ORDER_UNITS)
                .plus(processingTimes.perUnit(Stage.INSPECTION).multipliedBy(ORDER_UNITS));
        Duration delay = timeToDispatch.minus(receptionTimes.maneuver().dividedBy(2));
        return delay.isNegative() ? Duration.ZERO : delay;
    }

    /** Registra los camiones a su hora y sigue el trabajo hasta que termina para informar cómo salió. */
    private void run(PreventionMode mode, Duration delay, long deadlocksBefore) {
        try {
            clock.sleep(delay);
            reception.registerArrival(TRUCK_PRODUCT, TRUCK_UNITS, TRUCKS);
            while (!workFinished.getAsBoolean()) {
                Thread.sleep(FINISH_POLL_MILLIS);
            }
            boolean hadDeadlock = deadlocksDetected.getAsLong() > deadlocksBefore;
            status = new ScenarioSnapshot(hadDeadlock ? ScenarioPhase.FINISHED_WITH_DEADLOCK : ScenarioPhase.FINISHED_CLEAN, mode);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // la aplicación se está cerrando
        } catch (RuntimeException e) {
            LOG.error("The deadlock scenario failed", e);
            status = new ScenarioSnapshot(ScenarioPhase.IDLE, mode);
        }
    }

    /** Prefiere un cliente Exprés (el nivel no cambia el resultado, pero es el que más se parece a un pedido urgente). */
    private Customer pickCustomer() {
        return customers.all().stream()
                .filter(customer -> customer.level() == ServiceLevel.EXPRESS)
                .findFirst()
                .or(() -> customers.all().stream().findFirst())
                .orElseThrow(() -> new StateConflictException("There are no customers to place the scenario order"));
    }
}
