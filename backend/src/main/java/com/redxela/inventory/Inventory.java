package com.redxela.inventory;

import com.redxela.catalog.ProductCatalog;
import com.redxela.domain.Order;
import com.redxela.domain.OrderLine;
import com.redxela.domain.Product;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Existencias de cada producto y las reservas de los pedidos en proceso.
 *
 * <h3>Reserva de todo o nada</h3>
 * Un pedido reserva <b>todas</b> sus líneas en una sola operación atómica, o ninguna. Dos
 * consecuencias: no hay sobreventa (verificar y descontar ocurren bajo el mismo lock, sin hueco
 * entre ambos) y un pedido nunca retiene stock parcial mientras espera el resto, que sería
 * <i>hold-and-wait</i> sobre el inventario.
 *
 * <h3>Ciclo de vida</h3>
 * {@code available → reserved} al reservar; al despachar el pedido el stock sale ({@link #consume});
 * si el pedido se aborta vuelve a {@code available} ({@link #release}).
 *
 * <h3>Sincronización</h3>
 * Un {@link ReentrantReadWriteLock}: las consultas (snapshots de la interfaz, varias por segundo)
 * comparten el lock de lectura, y las modificaciones, que son raras, toman el de escritura.
 * Todo el estado se accede solo con el lock correspondiente tomado.
 */
public final class Inventory {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<String, Stock> stockByProduct = new LinkedHashMap<>();
    private final Map<String, Map<String, Integer>> reservations = new HashMap<>();

    /** Inventario con las mismas unidades iniciales de cada producto del catálogo. */
    public Inventory(ProductCatalog catalog, int initialUnitsPerProduct) {
        if (initialUnitsPerProduct < 0) {
            throw new IllegalArgumentException("initialUnitsPerProduct must not be negative");
        }
        for (Product product : catalog.all()) {
            stockByProduct.put(product.id(), new Stock(initialUnitsPerProduct));
        }
    }

    /**
     * Suma unidades recibidas al stock disponible.
     *
     * @throws IllegalArgumentException si el producto no existe o la cantidad no es positiva
     */
    public void receive(String productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        withLock(lock.writeLock(), () -> stockOf(productId).available += quantity);
    }

    /** Indica si hay stock disponible para todas las líneas del pedido. */
    public boolean canFulfill(Order order) {
        return missingFor(order).isEmpty();
    }

    /** Unidades que faltan de cada producto para atender el pedido; vacío si hay stock suficiente. */
    public List<MissingStock> missingFor(Order order) {
        Map<String, Integer> needed = unitsByProduct(order);
        lock.readLock().lock();
        try {
            return missingLocked(needed);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Reserva todas las líneas del pedido de forma atómica.
     *
     * @return {@code true} si se reservó todo; {@code false} (sin reservar nada) si falta stock de algo
     * @throws IllegalStateException    si el pedido ya tenía una reserva
     * @throws IllegalArgumentException si el pedido incluye un producto desconocido
     */
    public boolean reserve(Order order) {
        Map<String, Integer> needed = unitsByProduct(order);
        lock.writeLock().lock();
        try {
            if (reservations.containsKey(order.id())) {
                throw new IllegalStateException("Order " + order.id() + " already has a reservation");
            }
            if (!missingLocked(needed).isEmpty()) {
                return false;
            }
            needed.forEach((productId, units) -> {
                Stock stock = stockOf(productId);
                stock.available -= units;
                stock.reserved += units;
            });
            reservations.put(order.id(), needed);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * El pedido se despachó: las unidades reservadas salen del inventario.
     *
     * @throws IllegalStateException si el pedido no tenía reserva
     */
    public void consume(String orderId) {
        withLock(lock.writeLock(), () -> {
            Map<String, Integer> units = reservations.remove(orderId);
            if (units == null) {
                throw new IllegalStateException("Order " + orderId + " has no reservation to consume");
            }
            units.forEach((productId, quantity) -> stockOf(productId).reserved -= quantity);
        });
    }

    /** El pedido se abortó: las unidades reservadas vuelven a estar disponibles. No hace nada si no tenía reserva. */
    public void release(String orderId) {
        withLock(lock.writeLock(), () -> {
            Map<String, Integer> units = reservations.remove(orderId);
            if (units == null) {
                return;
            }
            units.forEach((productId, quantity) -> {
                Stock stock = stockOf(productId);
                stock.reserved -= quantity;
                stock.available += quantity;
            });
        });
    }

    /** Existencias de todos los productos, en el orden del catálogo. */
    public List<StockSnapshot> snapshot() {
        lock.readLock().lock();
        try {
            List<StockSnapshot> result = new ArrayList<>();
            stockByProduct.forEach((productId, stock) ->
                    result.add(new StockSnapshot(productId, stock.available, stock.reserved)));
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    // ---- Internos: se invocan con el lock correspondiente tomado ----

    private List<MissingStock> missingLocked(Map<String, Integer> needed) {
        List<MissingStock> missing = new ArrayList<>();
        needed.forEach((productId, units) -> {
            int shortfall = units - stockOf(productId).available;
            if (shortfall > 0) {
                missing.add(new MissingStock(productId, shortfall));
            }
        });
        return missing;
    }

    private Stock stockOf(String productId) {
        Stock stock = stockByProduct.get(productId);
        if (stock == null) {
            throw new IllegalArgumentException("Unknown product: " + productId);
        }
        return stock;
    }

    /** Unidades pedidas por producto; suma las líneas que repitan un producto. */
    private static Map<String, Integer> unitsByProduct(Order order) {
        Map<String, Integer> units = new LinkedHashMap<>();
        for (OrderLine line : order.lines()) {
            units.merge(line.product().id(), line.quantity(), Integer::sum);
        }
        return units;
    }

    private static void withLock(Lock lock, Runnable action) {
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    /** Contadores de un producto; solo se tocan con el lock de {@link Inventory} tomado. */
    private static final class Stock {
        private int available;
        private int reserved;

        private Stock(int available) {
            this.available = available;
        }
    }
}
