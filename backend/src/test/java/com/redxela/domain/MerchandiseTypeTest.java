package com.redxela.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Verifica las restricciones del enunciado sobre los tipos de mercancía. */
class MerchandiseTypeTest {

    @Test
    void defines_at_least_five_types() {
        assertThat(MerchandiseType.values()).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    void every_type_requires_a_different_combination_of_resources() {
        Set<Set<ResourceType>> combinations = new HashSet<>();
        for (MerchandiseType type : MerchandiseType.values()) {
            assertThat(combinations.add(Set.copyOf(type.recipe())))
                    .as("recipe of %s must be unique", type)
                    .isTrue();
        }
    }

    @Test
    void every_recipe_is_written_in_global_acquisition_order() {
        for (MerchandiseType type : MerchandiseType.values()) {
            List<ResourceType> sorted = type.recipe().stream()
                    .sorted(Comparator.comparingInt(ResourceType::rank))
                    .toList();
            assertThat(type.recipe()).as("recipe of %s", type).isEqualTo(sorted);
        }
    }

    @Test
    void at_least_two_types_contend_for_the_same_two_shared_resources() {
        Set<ResourceType> shared = Set.of(ResourceType.FORKLIFT, ResourceType.PACKING_STATION);
        long contenders = Arrays.stream(MerchandiseType.values())
                .filter(type -> type.recipe().containsAll(shared))
                .count();
        assertThat(contenders).isGreaterThanOrEqualTo(2);
    }
}
