package com.redxela.resources;

/** Clase de operación que compite por recursos; la interfaz la usa para distinguir pedidos de camiones. */
public enum OperationKind {

    /** El procesamiento de un pedido. */
    ORDER,
    /** La descarga de un camión en Recepción. */
    TRUCK,
    /** Cualquier otra (por ejemplo, operaciones de prueba). */
    OTHER
}
