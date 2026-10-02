package com.redxela.domain;

/**
 * Clases de recursos compartidos del centro de distribución.
 *
 * <p>El orden de declaración define el <b>orden global de adquisición</b> (rank): si todas las
 * operaciones piden sus recursos en este orden, nunca puede formarse una espera circular. Es la
 * base de la estrategia de prevención de deadlock "orden global de recursos".
 */
public enum ResourceType {

    // El encargado se asigna al admitir el pedido, por eso va primero en el orden global.
    WAREHOUSE_WORKER(10),
    FORKLIFT(3),
    QUALITY_STATION(4),
    PACKING_STATION(6),
    // Escáner y área de carga: el par que se invierte entre Recepción y Despacho (deadlock demo).
    SCANNER(1),
    LOADING_DOCK(2);

    private final int capacity;

    ResourceType(int capacity) {
        this.capacity = capacity;
    }

    /** Cantidad de unidades idénticas que existen de este recurso (fijada por el enunciado). */
    public int capacity() {
        return capacity;
    }

    /** Posición en el orden global de adquisición (menor = se adquiere antes). */
    public int rank() {
        return ordinal();
    }
}
