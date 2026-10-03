package com.redxela.warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WarehouseLayoutTest {

    @Test
    void the_default_warehouse_has_240_locations_of_1000_volume_units() {
        WarehouseLayout layout = WarehouseLayout.defaults();

        assertThat(layout.totalLocations()).isEqualTo(240);
        assertThat(layout.locationsPerAisle()).isEqualTo(40);
        assertThat(layout.locationCapacity()).isEqualTo(1_000);
    }

    @Test
    void every_index_maps_to_a_unique_coordinate_and_back() {
        WarehouseLayout layout = new WarehouseLayout(3, 4, 5, 100);

        for (int index = 0; index < layout.totalLocations(); index++) {
            int aisle = layout.aisleOf(index);
            int position = layout.positionOf(index);
            int level = layout.levelOf(index);
            assertThat(aisle).isBetween(0, 2);
            assertThat(position).isBetween(0, 3);
            assertThat(level).isBetween(0, 4);
            assertThat(layout.indexOf(aisle, position, level)).isEqualTo(index);
        }
    }

    @Test
    void numbers_locations_aisle_first_then_position_with_the_level_changing_fastest() {
        WarehouseLayout layout = new WarehouseLayout(2, 3, 4, 100);

        assertThat(layout.levelOf(0)).isZero();
        assertThat(layout.levelOf(1)).isEqualTo(1); // vecina vertical: el índice siguiente es el nivel de arriba
        assertThat(layout.positionOf(4)).isEqualTo(1); // tras 4 niveles empieza la posición siguiente
        assertThat(layout.aisleOf(12)).isEqualTo(1); // 3 posiciones × 4 niveles = 12 ubicaciones por pasillo
    }

    @Test
    void labels_are_one_based_and_readable() {
        WarehouseLayout layout = WarehouseLayout.defaults();

        assertThat(layout.label(0)).isEqualTo("A1-P1-N1");
        assertThat(layout.label(layout.indexOf(1, 2, 4))).isEqualTo("A2-P3-N5");
        assertThat(layout.label(layout.totalLocations() - 1)).isEqualTo("A6-P8-N5");
    }

    @Test
    void rejects_non_positive_dimensions() {
        assertThatThrownBy(() -> new WarehouseLayout(0, 1, 1, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WarehouseLayout(1, 0, 1, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WarehouseLayout(1, 1, 0, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WarehouseLayout(1, 1, 1, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
