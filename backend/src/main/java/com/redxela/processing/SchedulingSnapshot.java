package com.redxela.processing;

import com.redxela.scheduling.SchedulingPolicy;
import java.util.List;

/**
 * Estado de la planificación para la interfaz.
 *
 * @param policy       política de atención vigente
 * @param waitingTotal cantidad total de pedidos en espera
 * @param waiting      los primeros pedidos en espera, en el orden en que se atenderán; se limita su
 *                     cantidad para que el snapshot no crezca sin medida cuando la cola se acumula
 */
public record SchedulingSnapshot(SchedulingPolicy policy, int waitingTotal, List<WaitingOrderSnapshot> waiting) {

    public SchedulingSnapshot {
        waiting = List.copyOf(waiting);
    }
}
