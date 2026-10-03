package com.redxela.api;

import com.redxela.benchmark.BenchmarkSnapshot;
import com.redxela.deadlock.DeadlockSnapshot;
import com.redxela.demo.ScenarioSnapshot;
import com.redxela.generator.GeneratorSnapshot;
import com.redxela.inventory.InventorySnapshot;
import com.redxela.processing.OrderProgressSnapshot;
import com.redxela.processing.SchedulingSnapshot;
import com.redxela.reception.ReceptionSnapshot;
import com.redxela.resources.PoolSnapshot;
import com.redxela.sim.ClockSnapshot;
import com.redxela.stats.StatsSnapshot;
import com.redxela.warehouse.WarehouseSnapshot;
import java.util.List;

/**
 * Estado completo del centro de distribución que se envía a la interfaz. Crecerá a medida que se
 * agreguen inventario y estadísticas; la interfaz siempre recibe la foto entera, nunca diferencias,
 * para no depender del orden en que lleguen los mensajes.
 *
 * @param clock           reloj simulado
 * @param scheduling      política vigente y lista de espera
 * @param generator       estado del generador automático de pedidos
 * @param stats           estadísticas de los pedidos despachados
 * @param inventory       existencias, buffer de recepción y estado del procesamiento de Inventario
 * @param reception       camiones en Recepción
 * @param warehouse       ubicaciones del almacén y lotes
 * @param deadlock        prevención vigente y deadlock confirmado, si lo hay
 * @param scenario        estado del escenario de demostración del deadlock
 * @param benchmark       estado del comparador de políticas
 * @param resources       estado de cada pool de recursos
 * @param activeOrders    pedidos en proceso, en orden de llegada
 * @param completedOrders cantidad de pedidos ya despachados
 */
public record SimulationSnapshot(
        ClockSnapshot clock,
        SchedulingSnapshot scheduling,
        GeneratorSnapshot generator,
        StatsSnapshot stats,
        InventorySnapshot inventory,
        ReceptionSnapshot reception,
        WarehouseSnapshot warehouse,
        DeadlockSnapshot deadlock,
        ScenarioSnapshot scenario,
        BenchmarkSnapshot benchmark,
        List<PoolSnapshot> resources,
        List<OrderProgressSnapshot> activeOrders,
        long completedOrders) {
}
