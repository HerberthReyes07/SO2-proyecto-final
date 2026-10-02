package com.redxela.domain;

/**
 * Etapas por las que pasa todo pedido, en orden. Cada recurso pertenece a una etapa
 * ({@link ResourceType#stage()}).
 *
 * <p>El orden de las etapas coincide con el orden global de recursos: un pedido que avanza de
 * etapa solo pide recursos de rank mayor a los que ya tiene, lo que nunca forma espera circular
 * entre pedidos.
 */
public enum Stage {

    /** Un encargado recoge la mercancía de las estanterías (con montacargas si es pesada). */
    PICKING,
    /** Control de calidad. */
    INSPECTION,
    /** Empaque en una estación. */
    PACKING,
    /** Escaneo y carga del camión. */
    DISPATCH
}
