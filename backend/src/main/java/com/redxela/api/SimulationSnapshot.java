package com.redxela.api;

import com.redxela.resources.PoolSnapshot;
import com.redxela.sim.ClockSnapshot;
import java.util.List;

/**
 * Estado completo del centro de distribución que se envía a la interfaz. Crecerá a medida que se
 * agreguen pedidos, inventario y estadísticas; la interfaz siempre recibe la foto entera, nunca
 * diferencias, para no depender del orden en que lleguen los mensajes.
 */
public record SimulationSnapshot(ClockSnapshot clock, List<PoolSnapshot> resources) {
}
