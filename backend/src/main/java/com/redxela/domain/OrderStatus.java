package com.redxela.domain;

/** Estados por los que pasa un pedido durante su ciclo de vida. */
public enum OrderStatus {

    /** En la lista de espera, listo para ser atendido cuando haya recursos. */
    WAITING,
    /** En la lista de espera porque el inventario no tiene existencias suficientes. */
    WAITING_STOCK,
    /** Un encargado lo está procesando y ya retiene al menos un recurso. */
    PROCESSING,
    /** Despachado; ya liberó todos sus recursos. */
    COMPLETED
}
