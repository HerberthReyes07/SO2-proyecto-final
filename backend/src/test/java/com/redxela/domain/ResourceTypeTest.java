package com.redxela.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResourceTypeTest {

    @Test
    void stage_order_is_consistent_with_the_global_resource_order() {
        // Si un recurso de rank mayor perteneciera a una etapa anterior, avanzar de etapa podría
        // obligar a pedir un recurso de rank menor y reabriría la posibilidad de espera circular.
        for (ResourceType first : ResourceType.values()) {
            for (ResourceType second : ResourceType.values()) {
                if (first.rank() < second.rank()) {
                    assertThat(first.stage().ordinal())
                            .as("%s must not be in a later stage than %s", first, second)
                            .isLessThanOrEqualTo(second.stage().ordinal());
                }
            }
        }
    }

    @Test
    void capacities_match_the_statement() {
        assertThat(ResourceType.PACKING_STATION.capacity()).isEqualTo(6);
        assertThat(ResourceType.LOADING_DOCK.capacity()).isEqualTo(2);
        assertThat(ResourceType.WAREHOUSE_WORKER.capacity()).isEqualTo(10);
        assertThat(ResourceType.FORKLIFT.capacity()).isEqualTo(3);
        assertThat(ResourceType.QUALITY_STATION.capacity()).isEqualTo(4);
        assertThat(ResourceType.SCANNER.capacity()).isEqualTo(1);
    }
}
