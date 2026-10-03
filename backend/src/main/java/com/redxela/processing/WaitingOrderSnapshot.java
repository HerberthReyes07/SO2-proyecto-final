package com.redxela.processing;

import com.redxela.domain.ServiceLevel;
import com.redxela.inventory.MissingStock;
import java.util.List;

/**
 * Un pedido en la lista de espera tal como lo ve la interfaz.
 *
 * @param orderId          id del pedido
 * @param customerName     nombre del cliente
 * @param serviceLevel     nivel de servicio
 * @param totalItems       total de artículos
 * @param waitedMillis     tiempo simulado que lleva esperando
 * @param targetWaitMillis tiempo máximo de espera objetivo de su nivel; si {@code waitedMillis} lo supera, está retrasado
 * @param missingStock     productos de los que falta stock para atenderlo (vacío si hay para todo); mientras
 *                         no esté vacío el pedido no puede admitirse aunque haya encargados libres
 */
public record WaitingOrderSnapshot(
        String orderId,
        String customerName,
        ServiceLevel serviceLevel,
        int totalItems,
        long waitedMillis,
        long targetWaitMillis,
        List<MissingStock> missingStock) {

    public WaitingOrderSnapshot {
        missingStock = List.copyOf(missingStock);
    }
}
