package com.redxela.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.redxela.catalog.ProductCatalog;
import com.redxela.customers.CustomerRegistry;
import com.redxela.domain.Order;
import com.redxela.domain.OrderLine;
import com.redxela.domain.ServiceLevel;
import com.redxela.processing.OrderProcessor;
import com.redxela.processing.OrderScheduler;
import com.redxela.processing.OrderWorkflow;
import com.redxela.processing.ProcessingTimes;
import com.redxela.resources.ResourceRegistry;
import com.redxela.sim.SimClock;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderServiceTest {

    private static final String EXPRESS_CUSTOMER = "CUS-001"; // Aurora Tech, nivel Exprés (ver CustomerRegistry)

    private final SimClock clock = new SimClock();
    private OrderProcessor processor;
    private OrderScheduler scheduler;
    private OrderService service;

    @BeforeEach
    void setUp() {
        // El planificador no se arranca: los pedidos aceptados se quedan en la lista de espera,
        // que es justo lo que este test necesita observar (solo valida la creación).
        ResourceRegistry resources = new ResourceRegistry();
        OrderWorkflow workflow = new OrderWorkflow(resources, clock);
        processor = new OrderProcessor(workflow, ProcessingTimes.defaults());
        scheduler = new OrderScheduler(clock, resources, workflow, processor);
        service = new OrderService(ProductCatalog.defaultCatalog(), CustomerRegistry.withSampleCustomers(), scheduler);
    }

    @AfterEach
    void tearDown() {
        scheduler.close();
        processor.close();
    }

    @Test
    void a_valid_order_goes_to_the_waiting_list() {
        Order order = service.place(EXPRESS_CUSTOMER, List.of(new OrderLineRequest("P-001", 3)));

        assertThat(scheduler.snapshot(clock.now()).waiting())
                .extracting(waiting -> waiting.orderId())
                .containsExactly(order.id());
    }

    @Test
    void a_rejected_order_never_reaches_the_waiting_list() {
        assertThatThrownBy(() -> service.place("CUS-999", List.of(new OrderLineRequest("P-001", 1))))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(scheduler.snapshot(clock.now()).waitingTotal()).isZero();
    }

    @Test
    void creates_an_order_for_the_customer_with_sequential_ids() {
        Order first = service.place(EXPRESS_CUSTOMER, List.of(new OrderLineRequest("P-001", 3)));
        Order second = service.place(EXPRESS_CUSTOMER, List.of(new OrderLineRequest("P-007", 1)));

        assertThat(first.id()).isEqualTo("ORD-0001");
        assertThat(second.id()).isEqualTo("ORD-0002");
        assertThat(first.serviceLevel()).isEqualTo(ServiceLevel.EXPRESS);
        assertThat(first.totalItems()).isEqualTo(3);
    }

    @Test
    void sums_the_quantities_of_a_repeated_product() {
        Order order = service.place(EXPRESS_CUSTOMER, List.of(
                new OrderLineRequest("P-001", 2),
                new OrderLineRequest("P-004", 1),
                new OrderLineRequest("P-001", 3)));

        assertThat(order.lines()).extracting(line -> line.product().id()).containsExactly("P-001", "P-004");
        assertThat(order.lines()).extracting(OrderLine::quantity).containsExactly(5, 1);
    }

    @Test
    void rejects_an_unknown_customer() {
        assertThatThrownBy(() -> service.place("CUS-999", List.of(new OrderLineRequest("P-001", 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CUS-999");
    }

    @Test
    void rejects_an_unknown_product() {
        assertThatThrownBy(() -> service.place(EXPRESS_CUSTOMER, List.of(new OrderLineRequest("P-999", 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("P-999");
    }

    @Test
    void rejects_empty_orders() {
        assertThatThrownBy(() -> service.place(EXPRESS_CUSTOMER, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.place(EXPRESS_CUSTOMER, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_non_positive_quantities() {
        assertThatThrownBy(() -> service.place(EXPRESS_CUSTOMER, List.of(new OrderLineRequest("P-001", 0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.place(EXPRESS_CUSTOMER, List.of(new OrderLineRequest("P-001", -2))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_more_units_of_a_product_than_the_limit_even_when_split_across_lines() {
        int half = OrderService.MAX_QUANTITY_PER_LINE / 2 + 1;

        assertThatThrownBy(() -> service.place(EXPRESS_CUSTOMER, List.of(
                new OrderLineRequest("P-001", half), new OrderLineRequest("P-001", half))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_orders_with_too_many_different_products() {
        List<OrderLineRequest> tooMany = ProductCatalog.defaultCatalog().all().stream()
                .limit(OrderService.MAX_LINES + 1)
                .map(product -> new OrderLineRequest(product.id(), 1))
                .toList();

        assertThatThrownBy(() -> service.place(EXPRESS_CUSTOMER, tooMany)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void a_rejected_order_does_not_consume_an_order_number() {
        assertThatThrownBy(() -> service.place(EXPRESS_CUSTOMER, List.of(new OrderLineRequest("P-999", 1))))
                .isInstanceOf(IllegalArgumentException.class);

        Order order = service.place(EXPRESS_CUSTOMER, List.of(new OrderLineRequest("P-001", 1)));

        assertThat(order.id()).isEqualTo("ORD-0001");
    }
}
