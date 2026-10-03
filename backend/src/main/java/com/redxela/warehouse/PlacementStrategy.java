package com.redxela.warehouse;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Estrategias para elegir <i>cuáles</i> ubicaciones libres recibe un lote.
 *
 * <p>Como todas las ubicaciones tienen el mismo tamaño (los "marcos" de la paginación), no existe
 * fragmentación externa ni hace falta "ajustar" el tamaño del hueco: cualquier ubicación libre
 * sirve. Las estrategias solo se diferencian en dónde quedan los lotes.
 */
public enum PlacementStrategy {

    FIRST_FREE("Primera libre",
            "Toma las ubicaciones libres de menor índice: llena el almacén pasillo por pasillo. "
                    + "Mantiene los lotes juntos mientras hay espacio contiguo.") {
        @Override
        List<Integer> pick(WarehouseLayout layout, BitSet occupied, int count) {
            List<Integer> picked = new ArrayList<>(count);
            int index = occupied.nextClearBit(0);
            while (picked.size() < count && index < layout.totalLocations()) {
                picked.add(index);
                index = occupied.nextClearBit(index + 1);
            }
            return picked;
        }
    },

    LEAST_USED_AISLE("Pasillo menos ocupado",
            "Cada ubicación se toma del pasillo con menos ubicaciones ocupadas: reparte los lotes entre "
                    + "pasillos para equilibrar la carga de trabajo, a costa de dispersarlos más.") {
        @Override
        List<Integer> pick(WarehouseLayout layout, BitSet occupied, int count) {
            BitSet working = (BitSet) occupied.clone(); // simula la ocupación sin modificar la real
            int[] occupiedPerAisle = new int[layout.aisles()];
            for (int index = working.nextSetBit(0); index >= 0; index = working.nextSetBit(index + 1)) {
                occupiedPerAisle[layout.aisleOf(index)]++;
            }
            List<Integer> picked = new ArrayList<>(count);
            while (picked.size() < count) {
                int aisle = leastUsedAisleWithSpace(layout, working, occupiedPerAisle);
                if (aisle < 0) {
                    break; // no queda ninguna ubicación libre
                }
                int index = working.nextClearBit(aisle * layout.locationsPerAisle());
                working.set(index);
                occupiedPerAisle[aisle]++;
                picked.add(index);
            }
            return picked;
        }

        private int leastUsedAisleWithSpace(WarehouseLayout layout, BitSet working, int[] occupiedPerAisle) {
            int best = -1;
            for (int aisle = 0; aisle < layout.aisles(); aisle++) {
                int start = aisle * layout.locationsPerAisle();
                boolean hasSpace = working.nextClearBit(start) < start + layout.locationsPerAisle();
                if (hasSpace && (best < 0 || occupiedPerAisle[aisle] < occupiedPerAisle[best])) {
                    best = aisle;
                }
            }
            return best;
        }
    };

    private final String displayName;
    private final String description;

    PlacementStrategy(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    /** Explicación del criterio, pensada para mostrarse al usuario. */
    public String description() {
        return description;
    }

    /**
     * Elige hasta {@code count} ubicaciones libres, sin modificar {@code occupied}. Devuelve menos
     * si no hay tantas libres.
     */
    abstract List<Integer> pick(WarehouseLayout layout, BitSet occupied, int count);
}
