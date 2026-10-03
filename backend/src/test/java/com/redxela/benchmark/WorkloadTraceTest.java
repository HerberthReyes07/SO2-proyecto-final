package com.redxela.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.redxela.catalog.ProductCatalog;
import com.redxela.customers.CustomerRegistry;
import com.redxela.generator.WorkloadProfile;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkloadTraceTest {

    private static List<TraceEntry> trace(BenchmarkParams params) {
        return WorkloadTrace.generate(WorkloadProfile.defaults(), ProductCatalog.defaultCatalog(),
                CustomerRegistry.withSampleCustomers().all(), params);
    }

    @Test
    void the_same_seed_produces_the_same_trace() {
        BenchmarkParams params = new BenchmarkParams(50, 2.0, 7);

        assertThat(trace(params)).isEqualTo(trace(params));
    }

    @Test
    void a_different_seed_produces_a_different_trace() {
        assertThat(trace(new BenchmarkParams(50, 2.0, 1))).isNotEqualTo(trace(new BenchmarkParams(50, 2.0, 2)));
    }

    @Test
    void has_one_entry_per_order_in_chronological_order() {
        List<TraceEntry> entries = trace(new BenchmarkParams(80, 3.0, 5));

        assertThat(entries).hasSize(80);
        for (int i = 1; i < entries.size(); i++) {
            assertThat(entries.get(i).arrivalAt()).isGreaterThanOrEqualTo(entries.get(i - 1).arrivalAt());
        }
    }

    @Test
    void a_higher_rate_compresses_the_arrivals() {
        TraceEntry slowLast = trace(new BenchmarkParams(100, 1.0, 3)).getLast();
        TraceEntry fastLast = trace(new BenchmarkParams(100, 4.0, 3)).getLast();

        assertThat(fastLast.arrivalAt()).isLessThan(slowLast.arrivalAt());
    }

    @Test
    void rejects_out_of_range_parameters() {
        assertThatThrownBy(() -> new BenchmarkParams(BenchmarkParams.MIN_ORDERS - 1, 1.0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BenchmarkParams(BenchmarkParams.MAX_ORDERS + 1, 1.0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BenchmarkParams(50, 0, 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
