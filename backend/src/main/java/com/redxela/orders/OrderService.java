package com.redxela.orders;

import com.redxela.catalog.ProductCatalog;
import com.redxela.customers.CustomerRegistry;
import com.redxela.domain.Customer;
import com.redxela.domain.Order;
import com.redxela.domain.OrderLine;
import com.redxela.domain.Product;
import com.redxela.processing.OrderScheduler;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Punto de entrada para crear pedidos: valida lo que pide el usuario, construye el {@link Order}
 * y se lo entrega al planificador. Tanto el formulario manual como el generador automático pasarán por aquí.
 */
public final class OrderService {

    static final int MAX_LINES = 10;
    static final int MAX_QUANTITY_PER_LINE = 100;

    private final ProductCatalog catalog;
    private final CustomerRegistry customers;
    private final OrderScheduler scheduler;
    private final AtomicInteger nextNumber = new AtomicInteger();

    public OrderService(ProductCatalog catalog, CustomerRegistry customers, OrderScheduler scheduler) {
        this.catalog = catalog;
        this.customers = customers;
        this.scheduler = scheduler;
    }

    /**
     * Crea un pedido y lo manda a la lista de espera. Si una misma línea repite un producto, se suman sus cantidades.
     *
     * @throws IllegalArgumentException si el cliente o un producto no existen, o las cantidades no son válidas
     */
    public Order place(String customerId, List<OrderLineRequest> requests) {
        Customer customer = customers.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown customer: " + customerId));
        List<OrderLine> lines = buildLines(requests);

        Order order = new Order(String.format("ORD-%04d", nextNumber.incrementAndGet()), customer, lines);
        scheduler.enqueue(order);
        return order;
    }

    private List<OrderLine> buildLines(List<OrderLineRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("An order must have at least one line");
        }
        Map<String, Integer> quantityByProduct = new LinkedHashMap<>();
        for (OrderLineRequest request : requests) {
            if (request.quantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be positive for product " + request.productId());
            }
            quantityByProduct.merge(request.productId(), request.quantity(), Integer::sum);
        }
        if (quantityByProduct.size() > MAX_LINES) {
            throw new IllegalArgumentException("An order can have at most " + MAX_LINES + " different products");
        }

        List<OrderLine> lines = new ArrayList<>();
        quantityByProduct.forEach((productId, quantity) -> {
            Product product = catalog.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown product: " + productId));
            if (quantity > MAX_QUANTITY_PER_LINE) {
                throw new IllegalArgumentException(
                        "At most " + MAX_QUANTITY_PER_LINE + " units per product, but " + productId + " has " + quantity);
            }
            lines.add(new OrderLine(product, quantity));
        });
        return lines;
    }
}
