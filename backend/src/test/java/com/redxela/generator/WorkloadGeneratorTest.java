package com.redxela.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.redxela.catalog.ProductCatalog;
import com.redxela.customers.CustomerRegistry;
import com.redxela.domain.Customer;
import com.redxela.domain.ServiceLevel;
import com.redxela.orders.OrderLineRequest;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Las pruebas estadísticas usan una semilla fija: el resultado es siempre el mismo, nunca intermitente. */
class WorkloadGeneratorTest {

    private static final ProductCatalog CATALOG = ProductCatalog.defaultCatalog();
    private static final List<Customer> ONE_PER_LEVEL = CustomerRegistry.withSampleCustomers().all();
    private static final int SAMPLES = 20_000;

    private static WorkloadGenerator generator(long seed) {
        return new WorkloadGenerator(WorkloadProfile.defaults(), CATALOG, seed);
    }

    @Test
    void the_same_seed_produces_exactly_the_same_sequence() {
        WorkloadGenerator first = generator(7);
        WorkloadGenerator second = generator(7);

        for (int i = 0; i < 200; i++) {
            assertThat(second.nextArrivalWork()).isEqualTo(first.nextArrivalWork());
            assertThat(second.nextOrder(ONE_PER_LEVEL)).isEqualTo(first.nextOrder(ONE_PER_LEVEL));
        }
    }

    @Test
    void different_seeds_produce_different_sequences() {
        WorkloadGenerator first = generator(1);
        WorkloadGenerator second = generator(2);
        List<Object> firstDraws = new ArrayList<>();
        List<Object> secondDraws = new ArrayList<>();

        for (int i = 0; i < 50; i++) {
            firstDraws.add(first.nextOrder(ONE_PER_LEVEL));
            secondDraws.add(second.nextOrder(ONE_PER_LEVEL));
        }

        assertThat(firstDraws).isNotEqualTo(secondDraws);
    }

    @Test
    void the_service_level_mix_follows_the_profile_weights() {
        WorkloadGenerator generator = generator(11);
        Map<String, ServiceLevel> levelOfCustomer = ONE_PER_LEVEL.stream()
                .collect(Collectors.toMap(Customer::id, Customer::level));
        Map<ServiceLevel, Integer> counts = new EnumMap<>(ServiceLevel.class);

        for (int i = 0; i < SAMPLES; i++) {
            ServiceLevel level = levelOfCustomer.get(generator.nextOrder(ONE_PER_LEVEL).customerId());
            counts.merge(level, 1, Integer::sum);
        }

        // Pesos por defecto: 5 / 15 / 35 / 25 / 20 %.
        assertThat(share(counts, ServiceLevel.EXPRESS)).isBetween(0.04, 0.06);
        assertThat(share(counts, ServiceLevel.PRIORITY)).isBetween(0.14, 0.16);
        assertThat(share(counts, ServiceLevel.STANDARD)).isBetween(0.34, 0.36);
        assertThat(share(counts, ServiceLevel.SCHEDULED)).isBetween(0.24, 0.26);
        assertThat(share(counts, ServiceLevel.ECONOMY)).isBetween(0.19, 0.21);
    }

    private static double share(Map<ServiceLevel, Integer> counts, ServiceLevel level) {
        return counts.getOrDefault(level, 0) / (double) SAMPLES;
    }

    @Test
    void orders_have_one_to_three_distinct_products_with_sensible_quantities() {
        WorkloadGenerator generator = generator(3);
        double totalUnits = 0;
        int lines = 0;

        for (int i = 0; i < SAMPLES; i++) {
            List<OrderLineRequest> orderLines = generator.nextOrder(ONE_PER_LEVEL).lines();
            assertThat(orderLines).hasSizeBetween(1, 3);
            assertThat(orderLines.stream().map(OrderLineRequest::productId)).doesNotHaveDuplicates();
            for (OrderLineRequest line : orderLines) {
                assertThat(line.quantity()).isBetween(1, 30);
                assertThat(CATALOG.findById(line.productId())).isPresent();
                totalUnits += line.quantity();
                lines++;
            }
        }

        assertThat(totalUnits / lines).isBetween(4.5, 7.0); // media de unidades por línea
    }

    @Test
    void the_work_until_the_next_arrival_is_exponential_with_mean_one() {
        WorkloadGenerator generator = generator(5);
        double sum = 0;

        for (int i = 0; i < SAMPLES; i++) {
            double work = generator.nextArrivalWork();
            assertThat(work).isGreaterThanOrEqualTo(0.0).isFinite();
            sum += work;
        }

        assertThat(sum / SAMPLES).isBetween(0.95, 1.05);
    }

    @Test
    void falls_back_to_any_customer_when_none_has_the_chosen_level() {
        List<Customer> onlyEconomy = List.of(new Customer("CUS-9", "Solo económico", ServiceLevel.ECONOMY));
        WorkloadGenerator generator = generator(9);

        for (int i = 0; i < 200; i++) {
            assertThat(generator.nextOrder(onlyEconomy).customerId()).isEqualTo("CUS-9");
        }
    }

    @Test
    void refuses_to_generate_orders_when_there_are_no_customers() {
        assertThatThrownBy(() -> generator(1).nextOrder(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_an_unusable_profile() {
        assertThatThrownBy(() -> new WorkloadProfile(Map.of(ServiceLevel.EXPRESS, 0), List.of(1), 5, 30))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorkloadProfile(Map.of(ServiceLevel.EXPRESS, 1), List.of(), 5, 30))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorkloadProfile(Map.of(ServiceLevel.EXPRESS, 1), List.of(1), 5, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
