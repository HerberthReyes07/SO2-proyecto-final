package com.redxela.reception;

import java.time.Duration;

/**
 * Tiempos simulados de Recepción.
 *
 * @param unloadPerUnit descarga y registro por cada unidad que trae el camión
 * @param maneuver      maniobra para acomodar el camión en el andén, que ocurre justo después de tomar el andén
 *                      y antes de seguir; es el intervalo en el que el camión retiene el andén sin haber pedido
 *                      aún el escáner, lo que hace posible el deadlock con el orden inverso
 * @param retryBackoff  espera de un camión expulsado antes de volver a la fila; si reintentara de inmediato,
 *                      como los camiones pasan antes que los pedidos en las filas, recuperaría el recurso que
 *                      acaba de soltar y volvería a formar el deadlock
 */
public record ReceptionTimes(Duration unloadPerUnit, Duration maneuver, Duration retryBackoff) {

    public static ReceptionTimes defaults() {
        return new ReceptionTimes(Duration.ofSeconds(1), Duration.ofSeconds(10), Duration.ofMinutes(2));
    }
}
