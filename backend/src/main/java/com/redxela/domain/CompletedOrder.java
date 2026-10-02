package com.redxela.domain;

import java.time.Duration;
import java.util.Objects;

/**
 * Resumen de un pedido que ya se despachó; es lo que alimenta las estadísticas.
 *
 * @param orderId    id del pedido
 * @param level      nivel de servicio
 * @param totalItems total de artículos
 * @param waited     tiempo simulado en la lista de espera: desde que llegó hasta que se le asignó encargado
 * @param processing tiempo simulado en proceso: desde que se le asignó encargado hasta que terminó
 *                   (incluye las esperas por recursos durante el proceso)
 */
public record CompletedOrder(String orderId, ServiceLevel level, int totalItems, Duration waited, Duration processing) {

    public CompletedOrder {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(waited, "waited");
        Objects.requireNonNull(processing, "processing");
    }

    /** Indica si se empezó a atender dentro del tiempo máximo de espera de su nivel de servicio. */
    public boolean metTarget() {
        return waited.compareTo(level.targetWait()) <= 0;
    }
}
