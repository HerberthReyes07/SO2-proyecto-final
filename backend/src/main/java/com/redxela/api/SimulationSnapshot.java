package com.redxela.api;

import com.redxela.processing.OrderProgressSnapshot;
import com.redxela.processing.SchedulingSnapshot;
import com.redxela.resources.PoolSnapshot;
import com.redxela.sim.ClockSnapshot;
import java.util.List;

/**
 * Estado completo del centro de distribución que se envía a la interfaz. Crecerá a medida que se
 * agreguen inventario y estadísticas; la interfaz siempre recibe la foto entera, nunca diferencias,
 * para no depender del orden en que lleguen los mensajes.
 *
 * @param clock           reloj simulado
 * @param scheduling      política vigente y lista de espera
 * @param resources       estado de cada pool de recursos
 * @param activeOrders    pedidos en proceso, en orden de llegada
 * @param completedOrders cantidad de pedidos ya despachados
 */
public record SimulationSnapshot(
        ClockSnapshot clock,
        SchedulingSnapshot scheduling,
        List<PoolSnapshot> resources,
        List<OrderProgressSnapshot> activeOrders,
        long completedOrders) {
}
