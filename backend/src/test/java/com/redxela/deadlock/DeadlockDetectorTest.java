package com.redxela.deadlock;

import com.redxela.common.StateConflictException;
import static com.redxela.domain.ResourceType.LOADING_DOCK;
import static com.redxela.domain.ResourceType.SCANNER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.redxela.resources.Operation;
import com.redxela.resources.OperationKind;
import com.redxela.resources.OperationPreemptedException;
import com.redxela.resources.ResourcePool;
import com.redxela.resources.ResourceRegistry;
import com.redxela.sim.SimClock;
import com.redxela.testsupport.Await;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Pruebas del detector con pools e hilos reales. El deadlock de Recepción vs Despacho se arma a mano:
 * el pedido tiene el escáner y pide un andén; los dos camiones tienen los andenes y piden el escáner.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class DeadlockDetectorTest {

    /** Operación que se comporta como en la aplicación: si la expulsan, suelta lo que retiene. */
    private final class Actor implements Operation {
        private final String id;
        private final OperationKind kind;
        private final List<ResourcePool> held = new ArrayList<>();
        private final AtomicBoolean expelled = new AtomicBoolean();
        private Future<?> pending;

        private Actor(String id, OperationKind kind) {
            this.id = id;
            this.kind = kind;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public OperationKind kind() {
            return kind;
        }

        /** Toma el recurso ahora, en el hilo del test (no hay competencia: está libre). */
        void takeNow(ResourcePool pool) throws Exception {
            pool.acquire(this);
            held.add(pool);
        }

        /** Pide el recurso en otro hilo y, si lo expulsan, suelta todo lo que retiene (como un pedido o un camión). */
        void requestInBackground(ResourcePool pool) {
            pending = executor.submit(() -> {
                try {
                    pool.acquire(this);
                    held.add(pool);
                } catch (OperationPreemptedException e) {
                    expelled.set(true);
                    releaseAll();
                }
                return null;
            });
        }

        void releaseAll() {
            for (ResourcePool pool : List.copyOf(held)) {
                pool.release(this);
                held.remove(pool);
            }
        }
    }

    private final ResourceRegistry registry = new ResourceRegistry();
    private final SimClock clock = new SimClock();
    private final AcquisitionPolicy policy = new AcquisitionPolicy();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private DeadlockDetector detector;
    private Actor order;
    private Actor truck1;
    private Actor truck2;

    @BeforeEach
    void setUp() {
        detector = new DeadlockDetector(registry, clock, policy, Duration.ofMillis(20));
    }

    @AfterEach
    void tearDown() {
        detector.close();
        executor.shutdownNow();
    }

    /** Arma el deadlock: pedido con el escáner pidiendo un andén; camiones con los andenes pidiendo el escáner. */
    private void formDeadlock() throws Exception {
        ResourcePool scanner = registry.pool(SCANNER);
        ResourcePool docks = registry.pool(LOADING_DOCK);
        order = new Actor("ORD-0001", OperationKind.ORDER);
        truck1 = new Actor("TRK-0001", OperationKind.TRUCK);
        truck2 = new Actor("TRK-0002", OperationKind.TRUCK);
        truck1.takeNow(docks);
        truck2.takeNow(docks);
        order.takeNow(scanner);

        order.requestInBackground(docks);
        truck1.requestInBackground(scanner);
        truck2.requestInBackground(scanner);
        Await.until("everybody is blocked", () -> registry.pool(SCANNER).snapshot().waiting().size() == 2
                && registry.pool(LOADING_DOCK).snapshot().waiting().size() == 1);
    }

    private boolean nothingIsHeld() {
        return registry.snapshots().stream().allMatch(pool -> pool.holders().isEmpty());
    }

    @Test
    void reports_no_deadlock_while_the_system_is_healthy() {
        detector.scan();
        detector.scan();

        assertThat(detector.snapshot().current()).isNull();
        assertThat(detector.snapshot().detected()).isZero();
    }

    @Test
    void a_deadlock_is_only_confirmed_when_it_shows_up_in_two_consecutive_scans() throws Exception {
        formDeadlock();

        detector.scan();
        assertThat(detector.snapshot().current()).as("one sighting is not enough: it could be a stale read").isNull();

        detector.scan();
        assertThat(detector.snapshot().current()).isNotNull();
        assertThat(detector.snapshot().detected()).isEqualTo(1);
    }

    @Test
    void the_report_names_the_operations_the_resources_and_the_conflicting_pair() throws Exception {
        formDeadlock();
        detector.scan();
        detector.scan();

        DeadlockReport report = detector.snapshot().current();

        assertThat(report.id()).isEqualTo("DL-0001");
        assertThat(report.operations()).extracting(DeadlockedOperation::id)
                .containsExactly("ORD-0001", "TRK-0001", "TRK-0002");
        DeadlockedOperation reportedOrder = report.operations().get(0);
        assertThat(reportedOrder.kind()).isEqualTo(OperationKind.ORDER);
        assertThat(reportedOrder.held()).containsExactly(SCANNER);
        assertThat(reportedOrder.waitingFor()).isEqualTo(LOADING_DOCK);
        DeadlockedOperation reportedTruck = report.operations().get(1);
        assertThat(reportedTruck.kind()).isEqualTo(OperationKind.TRUCK);
        assertThat(reportedTruck.held()).containsExactly(LOADING_DOCK);
        assertThat(reportedTruck.waitingFor()).isEqualTo(SCANNER);
        assertThat(report.resources()).containsExactly(SCANNER, LOADING_DOCK);
        assertThat(report.conflict()).isEqualTo(new ConflictPair("ORD-0001", "TRK-0001"));
    }

    @Test
    void a_persisting_deadlock_keeps_its_id_and_is_counted_once() throws Exception {
        formDeadlock();
        detector.scan();
        detector.scan();
        String id = detector.snapshot().current().id();
        long detectedAt = detector.snapshot().current().detectedAtMillis();

        detector.scan();
        detector.scan();

        assertThat(detector.snapshot().current().id()).isEqualTo(id);
        assertThat(detector.snapshot().current().detectedAtMillis()).isEqualTo(detectedAt);
        assertThat(detector.snapshot().detected()).isEqualTo(1);
    }

    @Test
    void resolving_in_favour_of_the_order_expels_the_truck_and_lets_everything_finish() throws Exception {
        formDeadlock();
        detector.scan();
        detector.scan();

        DeadlockDetector.Resolution resolution = detector.resolve("ORD-0001");

        assertThat(resolution.continued()).isEqualTo("ORD-0001");
        assertThat(resolution.returnedToWaiting()).isEqualTo("TRK-0001");
        Await.until("the truck was expelled", () -> truck1.expelled.get());
        // Soltó su andén: el pedido lo toma, termina, suelta escáner y andén, y el otro camión por fin consigue el escáner.
        Await.until("order holds both resources", () -> order.held.size() == 2);
        order.releaseAll();
        Await.until("second truck got the scanner", () -> truck2.held.size() == 2);
        truck2.releaseAll();
        Await.until("everything released", this::nothingIsHeld);
        assertThat(truck2.expelled.get()).isFalse();
        assertThat(detector.snapshot().resolved()).isEqualTo(1);
        assertThat(detector.snapshot().current()).isNull();
    }

    @Test
    void resolving_in_favour_of_the_truck_expels_the_order_and_lets_the_trucks_finish() throws Exception {
        formDeadlock();
        detector.scan();
        detector.scan();

        DeadlockDetector.Resolution resolution = detector.resolve("TRK-0001");

        assertThat(resolution.returnedToWaiting()).isEqualTo("ORD-0001");
        Await.until("the order was expelled and released the scanner", () -> order.expelled.get() && order.held.isEmpty());
        Await.until("one truck got the scanner", () -> truck1.held.size() == 2 || truck2.held.size() == 2);
        truck1.releaseAll();
        Await.until("the other truck got it too", () -> truck2.held.size() == 2);
        truck2.releaseAll();
        Await.until("everything released", this::nothingIsHeld);
    }

    @Test
    void after_resolving_the_deadlock_is_gone_and_does_not_come_back() throws Exception {
        formDeadlock();
        detector.scan();
        detector.scan();
        detector.resolve("ORD-0001");
        Await.until("truck expelled", () -> truck1.expelled.get());

        detector.scan();
        detector.scan();

        assertThat(detector.snapshot().current()).isNull();
        assertThat(detector.snapshot().detected()).isEqualTo(1);
    }

    @Test
    void resolving_without_a_confirmed_deadlock_is_a_conflict() {
        assertThatThrownBy(() -> detector.resolve("ORD-0001")).isInstanceOf(StateConflictException.class);
    }

    @Test
    void resolving_in_favour_of_an_operation_that_is_not_in_the_conflict_pair_is_rejected() throws Exception {
        formDeadlock();
        detector.scan();
        detector.scan();

        assertThatThrownBy(() -> detector.resolve("TRK-0002"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ORD-0001")
                .hasMessageContaining("TRK-0001");
        assertThatThrownBy(() -> detector.resolve("NOBODY")).isInstanceOf(IllegalArgumentException.class);
        assertThat(detector.snapshot().current()).as("a rejected choice changes nothing").isNotNull();
        assertThat(detector.snapshot().resolved()).isZero();
    }

    @Test
    void a_resolution_that_arrives_after_the_deadlock_dissolved_by_itself_is_a_conflict() throws Exception {
        formDeadlock();
        detector.scan();
        detector.scan();
        // Antes de que el usuario decida, alguien suelta un andén: el pedido (la víctima si se elige al camión) lo consigue.
        truck2.releaseAll();
        Await.until("order got a dock", () -> order.held.size() == 2);

        assertThatThrownBy(() -> detector.resolve("TRK-0001")).isInstanceOf(StateConflictException.class);

        assertThat(detector.snapshot().resolved()).isZero();
        assertThat(order.expelled.get()).isFalse();
    }

    @Test
    void the_periodic_scan_finds_a_deadlock_without_being_asked() throws Exception {
        detector.start();

        formDeadlock();

        Await.until("deadlock confirmed by the background scan", () -> detector.snapshot().current() != null);
    }

    @Test
    void heavy_contention_with_a_single_global_order_is_never_reported_as_a_deadlock() throws Exception {
        // Todas piden escáner y luego andén (orden global): ningún ciclo es posible. El detector, escaneando
        // sin parar mientras los hilos compiten, no debe confirmar ninguno (ni siquiera por una lectura a medias).
        ResourcePool scanner = registry.pool(SCANNER);
        ResourcePool docks = registry.pool(LOADING_DOCK);
        AtomicBoolean running = new AtomicBoolean(true);
        List<Future<?>> workers = new ArrayList<>();
        for (int w = 0; w < 8; w++) {
            int id = w;
            workers.add(executor.submit(() -> {
                for (int i = 0; i < 400; i++) {
                    Actor actor = new Actor("W-" + id + "-" + i, OperationKind.TRUCK);
                    scanner.acquire(actor);
                    docks.acquire(actor);
                    docks.release(actor);
                    scanner.release(actor);
                }
                return null;
            }));
        }
        Future<?> scanning = executor.submit(() -> {
            while (running.get()) {
                detector.scan();
                if (detector.snapshot().current() != null) {
                    throw new AssertionError("A deadlock was reported where none is possible");
                }
            }
            return null;
        });

        for (Future<?> worker : workers) {
            worker.get(20, TimeUnit.SECONDS);
        }
        running.set(false);
        scanning.get(5, TimeUnit.SECONDS);

        assertThat(detector.snapshot().detected()).isZero();
    }
}
