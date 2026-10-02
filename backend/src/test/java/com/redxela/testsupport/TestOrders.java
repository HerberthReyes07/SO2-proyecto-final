package com.redxela.testsupport;

import com.redxela.catalog.ProductCatalog;
import com.redxela.domain.Customer;
import com.redxela.domain.Order;
import com.redxela.domain.OrderLine;
import com.redxela.domain.Product;
import com.redxela.domain.ServiceLevel;
import java.util.List;

/** Fábrica de pedidos de prueba con productos reales del catálogo. */
public final class TestOrders {

    public static final String STANDARD_PRODUCT = "P-001";
    public static final String FRAGILE_PRODUCT = "P-004";
    public static final String HEAVY_PRODUCT = "P-007";
    public static final String PERISHABLE_PRODUCT = "P-010";
    public static final String BULKY_PRODUCT = "P-013";

    private static final ProductCatalog CATALOG = ProductCatalog.defaultCatalog();
    private static final Customer CUSTOMER = new Customer("CUS-T", "Cliente de prueba", ServiceLevel.STANDARD);

    private TestOrders() {
    }

    public static Product product(String productId) {
        return CATALOG.findById(productId).orElseThrow();
    }

    /** Pedido de una sola línea. */
    public static Order order(String orderId, String productId, int units) {
        return new Order(orderId, CUSTOMER, List.of(new OrderLine(product(productId), units)));
    }

    /** Pedido de una sola línea para un cliente con el nivel de servicio indicado. */
    public static Order order(String orderId, ServiceLevel level, String productId, int units) {
        Customer customer = new Customer("CUS-" + level.level(), "Cliente " + level, level);
        return new Order(orderId, customer, List.of(new OrderLine(product(productId), units)));
    }
}
