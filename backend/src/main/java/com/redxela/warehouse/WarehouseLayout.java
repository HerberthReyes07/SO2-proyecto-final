package com.redxela.warehouse;

/**
 * Geometría del almacén: pasillos × posiciones × niveles, todas las ubicaciones del mismo tamaño.
 *
 * <p>Las ubicaciones se numeran con un índice lineal: primero por pasillo, luego por posición y,
 * lo que más rápido cambia, por nivel (ubicaciones apiladas en vertical son vecinas). Dos
 * ubicaciones son <b>contiguas</b> cuando sus índices son consecutivos.
 *
 * @param aisles           cantidad de pasillos
 * @param positions        posiciones por pasillo
 * @param levels           niveles por posición
 * @param locationCapacity capacidad de cada ubicación, en unidades de volumen
 */
public record WarehouseLayout(int aisles, int positions, int levels, int locationCapacity) {

    public WarehouseLayout {
        if (aisles < 1 || positions < 1 || levels < 1 || locationCapacity < 1) {
            throw new IllegalArgumentException("Every dimension of the warehouse must be positive");
        }
    }

    /**
     * Almacén de REDXela: 240 ubicaciones de 1.000 unidades de volumen. Con 300 unidades iniciales de
     * cada producto se ocupa menos de la mitad, lo que deja espacio para recibir mercadería y para
     * que, al irse vaciando lotes, las ubicaciones libres queden repartidas.
     */
    public static WarehouseLayout defaults() {
        return new WarehouseLayout(6, 8, 5, 1_000);
    }

    public int totalLocations() {
        return aisles * positions * levels;
    }

    /** Ubicaciones que tiene cada pasillo. */
    public int locationsPerAisle() {
        return positions * levels;
    }

    public int indexOf(int aisle, int position, int level) {
        return (aisle * positions + position) * levels + level;
    }

    public int aisleOf(int index) {
        return index / locationsPerAisle();
    }

    public int positionOf(int index) {
        return (index / levels) % positions;
    }

    public int levelOf(int index) {
        return index % levels;
    }

    /** Código legible de una ubicación, ej. {@code A2-P3-N1} (pasillo 2, posición 3, nivel 1; todo desde 1). */
    public String label(int index) {
        return "A" + (aisleOf(index) + 1) + "-P" + (positionOf(index) + 1) + "-N" + (levelOf(index) + 1);
    }
}
