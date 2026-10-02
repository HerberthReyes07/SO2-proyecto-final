package com.redxela.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class OrderTest {

    private static final Customer CUSTOMER = new Customer("C-1", "Ana", ServiceLevel.PRIORITY);
    private static final Product SHIRT = new Product("P-1", "Camisa", MerchandiseType.STANDARD, 2);
    private static final Product FRIDGE = new Product("P-2", "Refrigeradora", MerchandiseType.HEAVY, 40);

    @Test
    void totals_items_across_lines() {
        Order order = new Order("O-1", CUSTOMER, List.of(new OrderLine(SHIRT, 3), new OrderLine(FRIDGE, 2)));

        assertThat(order.totalItems()).isEqualTo(5);
    }

    @Test
    void takes_service_level_from_its_customer() {
        Order order = new Order("O-1", CUSTOMER, List.of(new OrderLine(SHIRT, 1)));

        assertThat(order.serviceLevel()).isEqualTo(ServiceLevel.PRIORITY);
    }

    @Test
    void mixed_order_requires_union_of_recipes_in_global_order() {
        Order order = new Order("O-1", CUSTOMER, List.of(new OrderLine(SHIRT, 1), new OrderLine(FRIDGE, 1)));

        assertThat(order.requiredResources()).containsExactly(
                ResourceType.FORKLIFT,
                ResourceType.PACKING_STATION,
                ResourceType.SCANNER,
                ResourceType.LOADING_DOCK);
    }

    @Test
    void starts_waiting() {
        Order order = new Order("O-1", CUSTOMER, List.of(new OrderLine(SHIRT, 1)));

        assertThat(order.status()).isEqualTo(OrderStatus.WAITING);
    }

    @Test
    void rejects_order_without_lines() {
        assertThatThrownBy(() -> new Order("O-1", CUSTOMER, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_non_positive_quantity() {
        assertThatThrownBy(() -> new OrderLine(SHIRT, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
