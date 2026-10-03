package com.redxela.warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.redxela.catalog.ProductCatalog;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Producto del catálogo → unidades por ubicación (capacidad 1.000): camiseta P-001 (vol. 2) = 500;
 * novela P-002 (vol. 2) = 500; vasos P-004 (vol. 6) = 166; refrigeradora P-007 (vol. 60) = 16;
 * colchón P-013 (vol. 80) = 12.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class WarehouseTest {

    private static final ProductCatalog CATALOG = ProductCatalog.defaultCatalog();
    private static final String SHIRT = "P-001";
    private static final String NOVEL = "P-002";
    private static final String FRIDGE = "P-007";
    private static final String MATTRESS = "P-013";

    private static Warehouse warehouse(int aisles, int positions, int levels) {
        return new Warehouse(new WarehouseLayout(aisles, positions, levels, 1_000), CATALOG, PlacementStrategy.FIRST_FREE);
    }

    /** 2 pasillos × 2 posiciones × 2 niveles = 8 ubicaciones. */
    private static Warehouse smallWarehouse() {
        return warehouse(2, 2, 2);
    }

    private static LotSnapshot lot(WarehouseSnapshot snapshot, String lotId) {
        return snapshot.lots().stream().filter(candidate -> candidate.lotId().equals(lotId)).findFirst().orElseThrow();
    }

    private static int unitsOf(WarehouseSnapshot snapshot, String productId) {
        return snapshot.lots().stream().filter(lot -> lot.productId().equals(productId)).mapToInt(LotSnapshot::units).sum();
    }

    @Test
    void units_per_location_depend_on_the_volume_of_the_unit_rounded_down() {
        Warehouse warehouse = smallWarehouse();

        assertThat(warehouse.unitsPerLocation(SHIRT)).isEqualTo(500);
        assertThat(warehouse.unitsPerLocation(FRIDGE)).isEqualTo(16);
        assertThat(warehouse.unitsPerLocation(MATTRESS)).isEqualTo(12);
    }

    @Test
    void a_small_lot_takes_one_whole_location_and_wastes_the_rest_of_it() {
        Warehouse warehouse = smallWarehouse();

        StoreResult result = warehouse.store("L1", SHIRT, 10);

        assertThat(result.stored()).isEqualTo(10);
        assertThat(result.rejected()).isZero();
        assertThat(result.locations()).hasSize(1);
        WarehouseSnapshot snapshot = warehouse.snapshot();
        assertThat(snapshot.usedLocations()).isEqualTo(1);
        assertThat(snapshot.locations().get(result.locations().get(0))).isEqualTo(new LocationContent("L1", SHIRT, 10, 20));
        assertThat(snapshot.wastedVolume()).isEqualTo(980); // fragmentación interna: 20 de 1.000 usados
    }

    @Test
    void a_large_lot_spreads_over_as_many_locations_as_it_needs_with_only_the_last_one_partly_filled() {
        Warehouse warehouse = smallWarehouse();

        StoreResult result = warehouse.store("L1", SHIRT, 1_200); // 500 + 500 + 200

        assertThat(result.locations()).hasSize(3);
        WarehouseSnapshot snapshot = warehouse.snapshot();
        List<Integer> unitsPerLocation = result.locations().stream().map(index -> snapshot.locations().get(index).units()).toList();
        assertThat(unitsPerLocation).containsExactly(500, 500, 200);
        assertThat(snapshot.wastedVolume()).isEqualTo(600); // solo el último queda a medias: 1.000 - 400
    }

    @Test
    void indivisible_units_leave_unused_space_in_every_full_location() {
        Warehouse warehouse = smallWarehouse();

        warehouse.store("L1", MATTRESS, 24); // 12 por ubicación: dos ubicaciones llenas

        // 12 colchones de 80 = 960 de 1.000: sobran 40 en cada una aunque estén "llenas".
        assertThat(warehouse.snapshot().wastedVolume()).isEqualTo(80);
    }

    @Test
    void first_free_assigns_the_lowest_free_locations_in_order() {
        Warehouse warehouse = smallWarehouse();

        StoreResult first = warehouse.store("L1", SHIRT, 600); // 2 ubicaciones
        StoreResult second = warehouse.store("L2", NOVEL, 100); // 1 ubicación

        assertThat(first.locations()).containsExactly(0, 1);
        assertThat(second.locations()).containsExactly(2);
    }

    @Test
    void a_new_lot_fills_the_holes_left_by_old_ones_so_it_ends_up_non_contiguous() {
        Warehouse warehouse = smallWarehouse();
        warehouse.store("A", SHIRT, 500);   // ubicación 0
        warehouse.store("B", NOVEL, 500);   // ubicación 1
        warehouse.store("C", SHIRT, 500);   // ubicación 2
        warehouse.retrieve(NOVEL, 500);     // B se agota: queda un hueco en la ubicación 1

        StoreResult result = warehouse.store("D", "P-005", 150); // jarrones: 100 por ubicación → necesita 2

        // El primer hueco es el de la ubicación 1 y el siguiente libre es la 3: el lote queda partido.
        assertThat(result.locations()).containsExactly(1, 3);
        LotSnapshot lotD = lot(warehouse.snapshot(), "D");
        assertThat(lotD.locations()).containsExactly(1, 3);
        assertThat(lotD.runs()).isEqualTo(2); // dos tramos separados por la ubicación 2 (del lote C)
    }

    @Test
    void a_lot_that_is_all_together_reports_a_single_run() {
        Warehouse warehouse = smallWarehouse();
        warehouse.store("A", SHIRT, 1_500); // ubicaciones 0, 1, 2

        assertThat(lot(warehouse.snapshot(), "A").runs()).isEqualTo(1);
    }

    @Test
    void retrieving_consumes_the_oldest_lots_first_and_frees_the_locations_that_empty() {
        Warehouse warehouse = smallWarehouse();
        warehouse.store("OLD", SHIRT, 500);
        warehouse.store("NEW", SHIRT, 500);

        warehouse.retrieve(SHIRT, 600);

        WarehouseSnapshot snapshot = warehouse.snapshot();
        assertThat(snapshot.lots()).extracting(LotSnapshot::lotId).containsExactly("NEW"); // OLD se agotó y desapareció
        assertThat(lot(snapshot, "NEW").units()).isEqualTo(400);
        assertThat(snapshot.usedLocations()).isEqualTo(1);
        assertThat(snapshot.locations().get(0)).isNull(); // la ubicación de OLD quedó libre
    }

    @Test
    void retrieving_part_of_a_location_keeps_it_occupied() {
        Warehouse warehouse = smallWarehouse();
        warehouse.store("A", SHIRT, 100);

        warehouse.retrieve(SHIRT, 30);

        WarehouseSnapshot snapshot = warehouse.snapshot();
        assertThat(snapshot.usedLocations()).isEqualTo(1);
        assertThat(lot(snapshot, "A").units()).isEqualTo(70);
        assertThat(snapshot.locations().get(0).usedVolume()).isEqualTo(140);
    }

    @Test
    void retrieving_only_touches_the_requested_product() {
        Warehouse warehouse = smallWarehouse();
        warehouse.store("A", SHIRT, 100);
        warehouse.store("B", NOVEL, 100);

        warehouse.retrieve(SHIRT, 100);

        WarehouseSnapshot snapshot = warehouse.snapshot();
        assertThat(unitsOf(snapshot, NOVEL)).isEqualTo(100);
        assertThat(unitsOf(snapshot, SHIRT)).isZero();
    }

    @Test
    void retrieving_more_than_there_is_fails_without_changing_anything() {
        Warehouse warehouse = smallWarehouse();
        warehouse.store("A", SHIRT, 100);

        assertThatThrownBy(() -> warehouse.retrieve(SHIRT, 101)).isInstanceOf(IllegalStateException.class);

        assertThat(unitsOf(warehouse.snapshot(), SHIRT)).isEqualTo(100);
    }

    @Test
    void stores_what_fits_and_rejects_the_rest_when_the_warehouse_runs_out_of_locations() {
        Warehouse warehouse = warehouse(1, 1, 2); // solo 2 ubicaciones: 1.000 camisetas

        StoreResult result = warehouse.store("A", SHIRT, 1_500);

        assertThat(result.stored()).isEqualTo(1_000);
        assertThat(result.rejected()).isEqualTo(500);
        assertThat(warehouse.snapshot().rejectedUnits()).isEqualTo(500);
        assertThat(warehouse.snapshot().usedLocations()).isEqualTo(2);
    }

    @Test
    void a_delivery_that_does_not_fit_at_all_creates_no_lot_and_counts_as_rejected() {
        Warehouse warehouse = warehouse(1, 1, 1);
        warehouse.store("A", SHIRT, 10);

        StoreResult result = warehouse.store("B", NOVEL, 100);

        assertThat(result.stored()).isZero();
        assertThat(result.rejected()).isEqualTo(100);
        assertThat(result.locations()).isEmpty();
        WarehouseSnapshot snapshot = warehouse.snapshot();
        assertThat(snapshot.lots()).extracting(LotSnapshot::lotId).containsExactly("A");
        assertThat(snapshot.rejectedUnits()).isEqualTo(100);
    }

    @Test
    void space_freed_by_dispatches_can_be_used_by_later_deliveries() {
        Warehouse warehouse = warehouse(1, 1, 1);
        warehouse.store("A", SHIRT, 500);
        assertThat(warehouse.store("B", NOVEL, 10).stored()).isZero();

        warehouse.retrieve(SHIRT, 500);

        assertThat(warehouse.store("C", NOVEL, 10).stored()).isEqualTo(10);
    }

    @Test
    void rejects_invalid_requests() {
        Warehouse warehouse = smallWarehouse();
        warehouse.store("A", SHIRT, 10);

        assertThatThrownBy(() -> warehouse.store("B", "P-999", 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> warehouse.store("B", SHIRT, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> warehouse.store("A", SHIRT, 10)).isInstanceOf(IllegalStateException.class); // lote repetido
        assertThatThrownBy(() -> warehouse.retrieve("P-999", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> warehouse.retrieve(SHIRT, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refuses_a_product_whose_single_unit_does_not_fit_in_a_location() {
        Warehouse tinyLocations = new Warehouse(new WarehouseLayout(1, 1, 1, 50), CATALOG, PlacementStrategy.FIRST_FREE);

        assertThatThrownBy(() -> tinyLocations.store("A", FRIDGE, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changing_the_strategy_only_affects_lots_that_arrive_afterwards() {
        Warehouse warehouse = smallWarehouse(); // 2 pasillos de 4 ubicaciones
        warehouse.store("A", SHIRT, 500);                       // FIRST_FREE: ubicación 0, pasillo 0

        warehouse.setStrategy(PlacementStrategy.LEAST_USED_AISLE);
        StoreResult result = warehouse.store("B", NOVEL, 500);  // el pasillo 1 está vacío: ahí va

        assertThat(warehouse.strategy()).isEqualTo(PlacementStrategy.LEAST_USED_AISLE);
        assertThat(result.locations()).containsExactly(4);
        assertThat(lot(warehouse.snapshot(), "A").locations()).containsExactly(0); // el lote viejo no se movió
    }

    @Test
    void random_operations_never_break_the_bitmap_the_lots_or_the_unit_count() {
        Warehouse warehouse = warehouse(3, 4, 5); // 60 ubicaciones
        Random random = new Random(7);
        String[] products = {SHIRT, NOVEL, "P-004", FRIDGE, MATTRESS};
        long stored = 0;
        long retrieved = 0;

        for (int step = 0; step < 600; step++) {
            String product = products[random.nextInt(products.length)];
            if (random.nextInt(3) > 0) {
                stored += warehouse.store("LOT-" + step, product, 1 + random.nextInt(400)).stored();
            } else {
                int available = unitsOf(warehouse.snapshot(), product);
                if (available > 0) {
                    int units = 1 + random.nextInt(available);
                    warehouse.retrieve(product, units);
                    retrieved += units;
                }
            }
            assertConsistent(warehouse.snapshot(), stored - retrieved);
        }
    }

    @Test
    void concurrent_stores_and_retrievals_keep_every_invariant() throws Exception {
        Warehouse warehouse = warehouse(6, 8, 5); // 240 ubicaciones
        int threads = 8;
        AtomicLong stored = new AtomicLong();
        AtomicLong retrieved = new AtomicLong();
        CountDownLatch startGate = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int thread = t;
                tasks.add(executor.submit(() -> {
                    startGate.await();
                    Random random = new Random(thread);
                    for (int i = 0; i < 300; i++) {
                        if (random.nextBoolean()) {
                            stored.addAndGet(warehouse.store("L-" + thread + "-" + i, SHIRT, 1 + random.nextInt(800)).stored());
                        } else {
                            tryRetrieve(warehouse, retrieved, 1 + random.nextInt(300));
                        }
                    }
                    return null;
                }));
            }
            startGate.countDown();
            for (Future<?> task : tasks) {
                task.get();
            }
        }

        assertConsistent(warehouse.snapshot(), stored.get() - retrieved.get());
    }

    /** Retira si hay unidades; entre la consulta y el retiro otro hilo puede haberlas llevado, y eso es válido. */
    private static void tryRetrieve(Warehouse warehouse, AtomicLong retrieved, int units) {
        try {
            warehouse.retrieve(SHIRT, units);
            retrieved.addAndGet(units);
        } catch (IllegalStateException notEnoughUnits) {
            // Otro hilo se llevó las unidades primero: el almacén rechazó el retiro sin modificar nada.
        }
    }

    /**
     * Invariantes: el bitmap coincide con las ubicaciones ocupadas, ninguna ubicación pertenece a dos
     * lotes, cada ubicación guarda unidades de un solo producto dentro de su capacidad, y no se
     * crearon ni perdieron unidades.
     */
    private static void assertConsistent(WarehouseSnapshot snapshot, long expectedUnits) {
        Set<Integer> occupiedByContent = new HashSet<>();
        long unitsInLocations = 0;
        for (int index = 0; index < snapshot.locations().size(); index++) {
            LocationContent content = snapshot.locations().get(index);
            if (content != null) {
                occupiedByContent.add(index);
                unitsInLocations += content.units();
                assertThat(content.units()).isPositive();
                assertThat(content.usedVolume()).isLessThanOrEqualTo(snapshot.layout().locationCapacity());
            }
        }
        assertThat(snapshot.usedLocations()).isEqualTo(occupiedByContent.size());

        Set<Integer> claimedByLots = new HashSet<>();
        long unitsInLots = 0;
        for (LotSnapshot lot : snapshot.lots()) {
            for (int index : lot.locations()) {
                assertThat(claimedByLots.add(index)).as("location %d claimed by two lots", index).isTrue();
                assertThat(snapshot.locations().get(index).lotId()).isEqualTo(lot.lotId());
                assertThat(snapshot.locations().get(index).productId()).isEqualTo(lot.productId());
            }
            unitsInLots += lot.units();
        }
        assertThat(claimedByLots).isEqualTo(occupiedByContent);
        assertThat(unitsInLocations).isEqualTo(unitsInLots).isEqualTo(expectedUnits);
    }
}
