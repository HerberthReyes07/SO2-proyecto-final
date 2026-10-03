package com.redxela.reception;

/** Fases por las que pasa un camión en Recepción. */
public enum TruckPhase {

    /** Esperando (o a punto de pedir) el escáner o el andén. */
    WAITING_RESOURCES,
    /** Con el andén asignado, acomodando el camión antes de seguir (puede retener ya otro recurso). */
    MANEUVERING,
    /** Con escáner y andén asignados, descargando y registrando la mercadería. */
    UNLOADING,
    /** Ya liberó sus recursos y está enviando el mensaje a Inventario (puede esperar si el buffer está lleno). */
    REPORTING,
    /** Fue expulsado para deshacer un deadlock; espera un rato antes de volver a la fila. */
    RETRYING
}
