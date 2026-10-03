package com.redxela.reception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.redxela.catalog.ProductCatalog;
import com.redxela.domain.ResourceType;
import com.redxela.inventory.ReceptionBuffer;
import com.redxela.inventory.ReceptionMessage;
import com.redxela.resources.PoolSnapshot;
import com.redxela.resources.ResourceRegistry;
import com.redxela.sim.SimClock;
import com.redxela.testsupport.Await;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class ReceptionServiceTest {

    private static final int BUFFER_CAPACITY = 2;

    private final ResourceRegistry registry = new ResourceRegistry();
    private final SimClock clock = new SimClock();
    private final ReceptionBuffer buffer = new ReceptionBuffer(BUFFER_CAPACITY);
    private ReceptionService service;

    @BeforeEach
    void setUp() {
        clock.setSpeed(SimClock.MAX_SPEED);
        service = new ReceptionService(registry, clock, buffer, ProductCatalog.defaultCatalog());
    }

    @AfterEach
    void tearDown() {
        service.close();
    }

    private List<TruckProgressSnapshot> trucks() {
        return service.snapshot(clock.now()).trucks();
    }

    private List<String> holdersOf(ResourceType type) {
        return registry.pool(type).snapshot().holders();
    }

    private boolean nothingIsHeld() {
        return registry.snapshots().stream().allMatch(pool -> pool.holders().isEmpty());
    }

    @Test
    void registers_trucks_with_sequential_ids() {
        clock.pause();

        List<String> ids = service.registerArrival("P-001", 10, 3);

        assertThat(ids).containsExactly("TRK-0001", "TRK-0002", "TRK-0003");
        assertThat(service.registerArrival("P-004", 5, 1)).containsExactly("TRK-0004");
    }

    @Test
    void rejects_invalid_arrivals() {
        assertThatThrownBy(() -> service.registerArrival("P-999", 10, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.registerArrival("P-001", 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.registerArrival("P-001", ReceptionService.MAX_QUANTITY_PER_TRUCK + 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.registerArrival("P-001", 10, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.registerArrival("P-001", 10, ReceptionService.MAX_TRUCKS_PER_REQUEST + 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(trucks()).isEmpty();
    }

    @Test
    void limits_the_number_of_trucks_pending_at_the_same_time() {
        clock.pause();
        for (int i = 0; i < ReceptionService.MAX_PENDING_TRUCKS / ReceptionService.MAX_TRUCKS_PER_REQUEST; i++) {
            service.registerArrival("P-001", 10, ReceptionService.MAX_TRUCKS_PER_REQUEST);
        }

        assertThatThrownBy(() -> service.registerArrival("P-001", 10, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Too many trucks");
    }

    @Test
    void a_truck_holds_the_scanner_and_a_dock_while_unloading_and_then_reports_to_inventory() throws Exception {
        clock.pause();
        service.registerArrival("P-007", 20, 1);

        Await.until("unloading", () -> trucks().stream().anyMatch(truck ->
                truck.phase() == TruckPhase.UNLOADING && truck.held().equals(List.of(ResourceType.SCANNER, ResourceType.LOADING_DOCK))));

        assertThat(holdersOf(ResourceType.SCANNER)).containsExactly("TRK-0001");
        assertThat(holdersOf(ResourceType.LOADING_DOCK)).containsExactly("TRK-0001");

        clock.resume();

        ReceptionMessage message = buffer.take();
        assertThat(message).isEqualTo(new ReceptionMessage("TRK-0001", "P-007", 20));
        Await.until("truck gone and resources free", () -> trucks().isEmpty() && nothingIsHeld());
    }

    @Test
    void a_truck_waiting_for_a_busy_resource_shows_what_it_awaits() throws Exception {
        clock.pause();
        service.registerArrival("P-001", 5, 2); // el segundo camión espera al primero: el escáner es uno solo

        Await.until("second truck waiting", () -> trucks().stream().anyMatch(truck ->
                truck.truckId().equals("TRK-0002") && truck.awaiting() == ResourceType.SCANNER));

        TruckProgressSnapshot waiting = trucks().stream().filter(truck -> truck.truckId().equals("TRK-0002")).findFirst().orElseThrow();
        assertThat(waiting.phase()).isEqualTo(TruckPhase.WAITING_RESOURCES);
        assertThat(waiting.held()).isEmpty();
    }

    @Test
    void trucks_release_the_scanner_and_dock_before_reporting_so_a_full_buffer_never_holds_them() throws Exception {
        // Nadie retira mensajes del buffer (capacidad 2): de 5 camiones, 2 caben y 3 quedan esperando espacio.
        int trucks = BUFFER_CAPACITY + 3;
        service.registerArrival("P-001", 10, trucks);

        Await.until("buffer full and three trucks waiting to report", () -> buffer.snapshot().size() == BUFFER_CAPACITY
                && buffer.snapshot().blockedProducers() == 3);

        // Lo crucial: los camiones bloqueados esperando espacio ya soltaron todo. Un Inventario lento
        // no puede dejar el único escáner ocupado ni paralizar los despachos.
        assertThat(nothingIsHeld()).isTrue();
        assertThat(trucks()).hasSize(3).allSatisfy(truck -> {
            assertThat(truck.phase()).isEqualTo(TruckPhase.REPORTING);
            assertThat(truck.held()).isEmpty();
            assertThat(truck.progress()).isEqualTo(1.0);
        });

        // Al drenar el buffer no se pierde ningún mensaje.
        List<String> delivered = new ArrayList<>();
        for (int i = 0; i < trucks; i++) {
            delivered.add(buffer.take().truckId());
        }
        assertThat(delivered).doesNotHaveDuplicates().hasSize(trucks);
        Await.until("all trucks finished", () -> trucks().isEmpty());
    }

    @Test
    void closing_the_service_releases_the_resources_of_trucks_in_the_middle_of_unloading() throws Exception {
        clock.pause();
        service.registerArrival("P-001", 10, 1);
        Await.until("unloading", () -> holdersOf(ResourceType.SCANNER).contains("TRK-0001"));

        service.close();

        Await.until("resources released and truck removed", () -> nothingIsHeld() && trucks().isEmpty());
        assertThat(buffer.snapshot().accepted()).isZero(); // interrumpido antes de reportar
    }

    @Test
    void unloading_progress_follows_the_simulated_clock() throws Exception {
        clock.pause();
        service.registerArrival("P-001", 100, 1);
        Await.until("unloading", () -> trucks().stream().anyMatch(truck -> truck.phase() == TruckPhase.UNLOADING));

        Duration halfway = clock.now().plus(ReceptionService.UNLOAD_TIME_PER_UNIT.multipliedBy(50));
        TruckProgressSnapshot truck = service.snapshot(halfway).trucks().get(0);

        assertThat(truck.progress()).isBetween(0.49, 0.51);
        PoolSnapshot scanner = registry.pool(ResourceType.SCANNER).snapshot();
        assertThat(scanner.holders()).containsExactly("TRK-0001");
    }
}
