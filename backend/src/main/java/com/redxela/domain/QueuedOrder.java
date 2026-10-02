package com.redxela.domain;

import java.time.Duration;
import java.util.Objects;

/**
 * Un pedido en la lista de espera, con los datos que las políticas de atención necesitan para
 * ordenarlo. Es inmutable: si un pedido vuelve a la lista (ej. tras un deadlock) conserva el mismo
 * registro, y por lo tanto su hora de llegada original.
 *
 * @param order       el pedido
 * @param arrivalTime instante simulado en que llegó al sistema
 * @param sequence    número de llegada, estrictamente creciente; desempata cuando dos claves coinciden
 */
public record QueuedOrder(Order order, Duration arrivalTime, long sequence) {

    public QueuedOrder {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(arrivalTime, "arrivalTime");
    }

    /** Instante simulado límite para empezar a atenderlo: llegada + tiempo objetivo de su nivel. */
    public Duration deadline() {
        return arrivalTime.plus(order.serviceLevel().targetWait());
    }

    /** Tiempo simulado que lleva esperando en el instante {@code now}. */
    public Duration waitedAt(Duration now) {
        Duration waited = now.minus(arrivalTime);
        return waited.isNegative() ? Duration.ZERO : waited;
    }
}
