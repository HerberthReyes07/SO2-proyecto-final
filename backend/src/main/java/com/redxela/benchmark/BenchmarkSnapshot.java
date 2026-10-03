package com.redxela.benchmark;

import com.redxela.scheduling.SchedulingPolicy;
import java.util.List;

/**
 * Estado de la comparación para la interfaz.
 *
 * @param phase           en qué punto está
 * @param params          la carga con la que corre, o null si nunca se lanzó
 * @param currentPolicy   la política que se está corriendo ahora, o null si no hay ninguna en marcha
 * @param policiesDone    cuántas políticas ya terminaron
 * @param policiesTotal   cuántas se compararán en total
 * @param ordersCompleted pedidos ya despachados por la política en marcha
 * @param results         resultados de las políticas terminadas, en el orden en que corrieron
 * @param error           motivo, si {@code phase} es {@code FAILED}
 */
public record BenchmarkSnapshot(
        BenchmarkPhase phase,
        BenchmarkParams params,
        SchedulingPolicy currentPolicy,
        int policiesDone,
        int policiesTotal,
        int ordersCompleted,
        List<PolicyResult> results,
        String error) {

    public BenchmarkSnapshot {
        results = List.copyOf(results);
    }

    /** Estado inicial: nada lanzado todavía. */
    public static BenchmarkSnapshot idle() {
        return new BenchmarkSnapshot(BenchmarkPhase.IDLE, null, null, 0, SchedulingPolicy.values().length, 0, List.of(), null);
    }
}
