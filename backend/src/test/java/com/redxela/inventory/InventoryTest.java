package com.redxela.inventory;

import static com.redxela.testsupport.TestOrders.HEAVY_PRODUCT;
import static com.redxela.testsupport.TestOrders.STANDARD_PRODUCT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.redxela.catalog.ProductCatalog;
import com.redxela.domain.Customer;
import com.redxela.domain.Order;
import com.redxela.domain.OrderLine;
import com.redxela.domain.ServiceLevel;
import com.redxela.testsupport.TestOrders;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class InventoryTest {

    private static final ProductCatalog CATALOG = ProductCatalog.defaultCatalog();

    private static Inventory inventoryWith(String productId, int units) {
        Inventory inventory = new Inventory(CATALOG, 0);
        if (units > 0) {
            inventory.receive(productId, units);
        }
        return inventory;
    }

    private static StockSnapshot stockOf(Inventory inventory, String productId) {
        return inventory.snapshot().stream().filter(stock -> stock.productId().equals(productId)).findFirst().orElseThrow();
    }

    private static Order twoLineOrder(String id, int standardUnits, int heavyUnits) {
        Customer customer = new Customer("C", "Cliente", ServiceLevel.STANDARD);
        return new Order(id, customer, List.of(
                new OrderLine(TestOrders.product(STANDARD_PRODUCT), standardUnits),
                new OrderLine(TestOrders.product(HEAVY_PRODUCT), heavyUnits)));
    }

    @Test
    void starts_every_catalog_product_with_the_initial_units() {
        Inventory inventory = new Inventory(CATALOG, 300);

        assertThat(inventory.snapshot()).hasSameSizeAs(CATALOG.all());
        assertThat(inventory.snapshot()).allSatisfy(stock -> {
            assertThat(stock.available()).isEqualTo(300);
            assertThat(stock.reserved()).isZero();
        });
    }

    @Test
    void receiving_adds_to_the_available_stock() {
        Inventory inventory = inventoryWith(STANDARD_PRODUCT, 10);

        inventory.receive(STANDARD_PRODUCT, 25);

        assertThat(stockOf(inventory, STANDARD_PRODUCT).available()).isEqualTo(35);
    }

    @Test
    void rejects_receiving_an_unknown_product_or_a_non_positive_quantity() {
        Inventory inventory = new Inventory(CATALOG, 0);

        assertThatThrownBy(() -> inventory.receive("P-999", 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inventory.receive(STANDARD_PRODUCT, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inventory.receive(STANDARD_PRODUCT, -3)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reserving_moves_units_from_available_to_reserved() {
        Inventory inventory = inventoryWith(STANDARD_PRODUCT, 10);

        boolean reserved = inventory.reserve(TestOrders.order("O-1", STANDARD_PRODUCT, 4));

        assertThat(reserved).isTrue();
        assertThat(stockOf(inventory, STANDARD_PRODUCT)).isEqualTo(new StockSnapshot(STANDARD_PRODUCT, 6, 4));
    }

    @Test
    void reservation_is_all_or_nothing() {
        Inventory inventory = inventoryWith(STANDARD_PRODUCT, 10); // hay camisetas pero no refrigeradoras
        Order order = twoLineOrder("O-1", 5, 2);

        assertThat(inventory.reserve(order)).isFalse();

        // No quedó stock parcial retenido: ni siquiera de la línea que sí tenía existencias.
        assertThat(stockOf(inventory, STANDARD_PRODUCT)).isEqualTo(new StockSnapshot(STANDARD_PRODUCT, 10, 0));
        assertThat(stockOf(inventory, HEAVY_PRODUCT)).isEqualTo(new StockSnapshot(HEAVY_PRODUCT, 0, 0));
    }

    @Test
    void reports_exactly_what_is_missing() {
        Inventory inventory = inventoryWith(STANDARD_PRODUCT, 3);

        List<MissingStock> missing = inventory.missingFor(twoLineOrder("O-1", 5, 2));

        assertThat(missing).containsExactly(new MissingStock(STANDARD_PRODUCT, 2), new MissingStock(HEAVY_PRODUCT, 2));
        assertThat(inventory.canFulfill(twoLineOrder("O-2", 3, 1))).isFalse();
        assertThat(inventory.canFulfill(TestOrders.order("O-3", STANDARD_PRODUCT, 3))).isTrue();
    }

    @Test
    void sums_the_units_when_an_order_repeats_a_product() {
        Inventory inventory = inventoryWith(STANDARD_PRODUCT, 5);
        Customer customer = new Customer("C", "Cliente", ServiceLevel.STANDARD);
        Order repeated = new Order("O-1", customer, List.of(
                new OrderLine(TestOrders.product(STANDARD_PRODUCT), 3),
                new OrderLine(TestOrders.product(STANDARD_PRODUCT), 3)));

        assertThat(inventory.canFulfill(repeated)).isFalse(); // 3 + 3 = 6 > 5
        assertThat(inventory.missingFor(repeated)).containsExactly(new MissingStock(STANDARD_PRODUCT, 1));
    }

    @Test
    void an_order_cannot_reserve_twice() {
        Inventory inventory = inventoryWith(STANDARD_PRODUCT, 10);
        Order order = TestOrders.order("O-1", STANDARD_PRODUCT, 2);
        inventory.reserve(order);

        assertThatThrownBy(() -> inventory.reserve(order)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void consuming_removes_the_reserved_units_from_the_inventory() {
        Inventory inventory = inventoryWith(STANDARD_PRODUCT, 10);
        inventory.reserve(TestOrders.order("O-1", STANDARD_PRODUCT, 4));

        inventory.consume("O-1");

        assertThat(stockOf(inventory, STANDARD_PRODUCT)).isEqualTo(new StockSnapshot(STANDARD_PRODUCT, 6, 0));
    }

    @Test
    void consuming_an_order_without_reservation_is_an_error() {
        Inventory inventory = new Inventory(CATALOG, 10);

        assertThatThrownBy(() -> inventory.consume("O-404")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void releasing_returns_the_reserved_units_and_is_idempotent() {
        Inventory inventory = inventoryWith(STANDARD_PRODUCT, 10);
        inventory.reserve(TestOrders.order("O-1", STANDARD_PRODUCT, 4));

        inventory.release("O-1");
        inventory.release("O-1");     // repetir no devuelve unidades de más
        inventory.release("O-404");   // un pedido sin reserva se ignora

        assertThat(stockOf(inventory, STANDARD_PRODUCT)).isEqualTo(new StockSnapshot(STANDARD_PRODUCT, 10, 0));
    }

    @Test
    void a_released_order_can_reserve_again() {
        Inventory inventory = inventoryWith(STANDARD_PRODUCT, 10);
        Order order = TestOrders.order("O-1", STANDARD_PRODUCT, 4);
        inventory.reserve(order);
        inventory.release("O-1");

        assertThat(inventory.reserve(order)).isTrue();
    }

    @Test
    void concurrent_reservations_never_oversell() throws Exception {
        int threads = 16;
        int attemptsPerThread = 500;
        int stock = 3_000; // menos que los 8.000 intentos: la mitad tiene que perder
        Inventory inventory = inventoryWith(STANDARD_PRODUCT, stock);
        AtomicInteger succeeded = new AtomicInteger();
        CountDownLatch startGate = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int thread = t;
                tasks.add(executor.submit(() -> {
                    startGate.await(); // todos arrancan a la vez para maximizar la competencia
                    for (int i = 0; i < attemptsPerThread; i++) {
                        if (inventory.reserve(TestOrders.order("O-" + thread + "-" + i, STANDARD_PRODUCT, 1))) {
                            succeeded.incrementAndGet();
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

        // Verificar y descontar son una sola operación: ni una unidad de más ni una de menos.
        assertThat(succeeded.get()).isEqualTo(stock);
        assertThat(stockOf(inventory, STANDARD_PRODUCT)).isEqualTo(new StockSnapshot(STANDARD_PRODUCT, 0, stock));
    }

    @Test
    void no_unit_is_ever_created_or_lost_under_concurrent_receiving_reserving_consuming_and_releasing() throws Exception {
        int initial = 200;
        Inventory inventory = inventoryWith(STANDARD_PRODUCT, initial);
        AtomicInteger received = new AtomicInteger();
        AtomicInteger consumed = new AtomicInteger();

        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int worker = 0; worker < 8; worker++) {
                int id = worker;
                tasks.add(executor.submit(() -> {
                    Random random = new Random(id);
                    for (int i = 0; i < 400; i++) {
                        int units = 1 + random.nextInt(5);
                        if (id == 0) {
                            inventory.receive(STANDARD_PRODUCT, units);
                            received.addAndGet(units);
                            continue;
                        }
                        Order order = TestOrders.order("O-" + id + "-" + i, STANDARD_PRODUCT, units);
                        if (inventory.reserve(order)) {
                            if (random.nextBoolean()) {
                                inventory.consume(order.id());
                                consumed.addAndGet(units);
                            } else {
                                inventory.release(order.id());
                            }
                        }
                    }
                }));
            }
            for (Future<?> task : tasks) {
                task.get();
            }
        }

        StockSnapshot stock = stockOf(inventory, STANDARD_PRODUCT);
        assertThat(stock.reserved()).isZero(); // todo pedido terminó consumiendo o liberando
        assertThat(stock.available()).isEqualTo(initial + received.get() - consumed.get());
        assertThat(stock.available()).isNotNegative();
    }
}
