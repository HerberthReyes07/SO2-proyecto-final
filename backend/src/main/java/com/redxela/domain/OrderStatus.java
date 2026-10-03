package com.redxela.domain;

/** Estados por los que pasa un pedido durante su ciclo de vida. */
public enum OrderStatus {

    /**
     * En la lista de espera. Puede ser porque no hay encargado libre o porque el inventario no tiene
     * existencias suficientes; esto último no es un estado aparte, se deduce del inventario al
     * consultar la lista (así nunca queda desactualizado).
     */
    WAITING,
    /** Un encargado lo está procesando y ya retiene al menos un recurso. */
    PROCESSING,
    /** Despachado; ya liberó todos sus recursos. */
    COMPLETED
}
