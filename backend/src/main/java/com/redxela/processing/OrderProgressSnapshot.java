package com.redxela.processing;

import com.redxela.domain.OrderStatus;
import com.redxela.domain.ResourceType;
import com.redxela.domain.ServiceLevel;
import com.redxela.domain.Stage;
import java.util.List;

/**
 * Foto del avance de un pedido en proceso, lo que consume la interfaz.
 *
 * @param orderId         id del pedido
 * @param customerName    nombre del cliente
 * @param serviceLevel    nivel de servicio
 * @param totalItems      total de artículos del pedido
 * @param status          estado del pedido
 * @param stage           etapa actual
 * @param awaiting        recurso que está esperando, o null si no espera ninguno
 * @param held            recursos que retiene en este momento
 * @param progress        avance del trabajo, entre 0 y 1
 * @param remainingMillis tiempo simulado de trabajo restante, en ms (no incluye esperas futuras por recursos)
 * @param retries         cuántas veces fue expulsado para deshacer un deadlock y devuelto a la lista de espera
 */
public record OrderProgressSnapshot(
        String orderId,
        String customerName,
        ServiceLevel serviceLevel,
        int totalItems,
        OrderStatus status,
        Stage stage,
        ResourceType awaiting,
        List<ResourceType> held,
        double progress,
        long remainingMillis,
        int retries) {

    public OrderProgressSnapshot {
        held = List.copyOf(held);
    }
}
