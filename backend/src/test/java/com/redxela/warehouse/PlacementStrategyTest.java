package com.redxela.warehouse;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.BitSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlacementStrategyTest {

    // 3 pasillos × 2 posiciones × 2 niveles = 12 ubicaciones, 4 por pasillo (índices 0-3, 4-7, 8-11).
    private final WarehouseLayout layout = new WarehouseLayout(3, 2, 2, 1_000);

    private static BitSet occupied(int... indexes) {
        BitSet bits = new BitSet();
        for (int index : indexes) {
            bits.set(index);
        }
        return bits;
    }

    @Test
    void first_free_takes_the_lowest_free_indexes_and_skips_occupied_ones() {
        BitSet occupied = occupied(0, 2);

        List<Integer> picked = PlacementStrategy.FIRST_FREE.pick(layout, occupied, 3);

        assertThat(picked).containsExactly(1, 3, 4);
    }

    @Test
    void first_free_returns_fewer_when_there_are_not_enough_free_locations() {
        BitSet occupied = occupied(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

        assertThat(PlacementStrategy.FIRST_FREE.pick(layout, occupied, 5)).containsExactly(10, 11);
    }

    @Test
    void least_used_aisle_spreads_the_locations_across_aisles() {
        List<Integer> picked = PlacementStrategy.LEAST_USED_AISLE.pick(layout, new BitSet(), 3);

        // Cada ubicación sale de un pasillo distinto: primero libre de cada uno.
        assertThat(picked).containsExactly(0, 4, 8);
    }

    @Test
    void least_used_aisle_prefers_the_emptiest_aisle_and_breaks_ties_by_lowest_aisle() {
        BitSet occupied = occupied(0, 1, 2, 4); // pasillo 0 con 3 ocupadas, pasillo 1 con 1, pasillo 2 vacío

        List<Integer> picked = PlacementStrategy.LEAST_USED_AISLE.pick(layout, occupied, 2);

        // Primero el pasillo 2 (vacío). Luego los pasillos 1 y 2 empatan con 1 ocupada cada uno: gana el menor, el 1,
        // cuyo primer índice libre es el 5 (el 4 ya está ocupado).
        assertThat(picked).containsExactly(8, 5);
    }

    @Test
    void least_used_aisle_skips_aisles_that_are_full_and_stops_when_nothing_is_left() {
        BitSet occupied = occupied(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10); // solo queda libre el índice 11

        assertThat(PlacementStrategy.LEAST_USED_AISLE.pick(layout, occupied, 4)).containsExactly(11);
    }

    @Test
    void neither_strategy_modifies_the_occupancy_it_is_given() {
        for (PlacementStrategy strategy : PlacementStrategy.values()) {
            BitSet occupied = occupied(1, 5);
            BitSet copy = (BitSet) occupied.clone();

            strategy.pick(layout, occupied, 4);

            assertThat(occupied).as("strategy %s", strategy).isEqualTo(copy);
        }
    }

    @Test
    void every_strategy_explains_itself_to_the_user() {
        for (PlacementStrategy strategy : PlacementStrategy.values()) {
            assertThat(strategy.displayName()).isNotBlank();
            assertThat(strategy.description()).isNotBlank();
        }
    }
}
