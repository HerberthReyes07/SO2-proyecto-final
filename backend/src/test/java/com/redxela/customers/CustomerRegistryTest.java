package com.redxela.customers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.redxela.domain.Customer;
import com.redxela.domain.ServiceLevel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class CustomerRegistryTest {

    @Test
    void sample_registry_has_one_customer_per_service_level() {
        CustomerRegistry registry = CustomerRegistry.withSampleCustomers();

        assertThat(registry.all()).extracting(Customer::level).containsExactlyInAnyOrder(ServiceLevel.values());
    }

    @Test
    void creates_customers_with_sequential_ids_and_chosen_level() {
        CustomerRegistry registry = new CustomerRegistry();

        Customer first = registry.create("  Ana  ", ServiceLevel.EXPRESS);
        Customer second = registry.create("Luis", ServiceLevel.ECONOMY);

        assertThat(first.id()).isEqualTo("CUS-001");
        assertThat(first.name()).isEqualTo("Ana"); // se recorta el espacio sobrante
        assertThat(first.level()).isEqualTo(ServiceLevel.EXPRESS);
        assertThat(second.id()).isEqualTo("CUS-002");
        assertThat(registry.findById("CUS-002")).contains(second);
    }

    @Test
    void rejects_blank_names_missing_levels_and_overlong_names() {
        CustomerRegistry registry = new CustomerRegistry();

        assertThatThrownBy(() -> registry.create("   ", ServiceLevel.STANDARD)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.create(null, ServiceLevel.STANDARD)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.create("Ana", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.create("x".repeat(CustomerRegistry.MAX_NAME_LENGTH + 1), ServiceLevel.STANDARD))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(registry.all()).isEmpty();
    }

    @Test
    void concurrent_creation_never_repeats_an_id() throws Exception {
        CustomerRegistry registry = new CustomerRegistry();
        List<String> ids = Collections.synchronizedList(new ArrayList<>());
        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                tasks.add(executor.submit(() -> ids.add(registry.create("Cliente", ServiceLevel.STANDARD).id())));
            }
            for (Future<?> task : tasks) {
                task.get();
            }
        }

        assertThat(ids).hasSize(200).doesNotHaveDuplicates();
        assertThat(registry.all()).hasSize(200);
    }
}
