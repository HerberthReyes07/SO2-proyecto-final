package com.redxela.deadlock;

/**
 * Estado del manejo de deadlock para la interfaz.
 *
 * @param prevention estrategia de prevención vigente
 * @param current    el deadlock confirmado ahora, o null si no hay ninguno
 * @param detected   deadlocks distintos confirmados desde el inicio
 * @param resolved   deadlocks resueltos desde el inicio
 */
public record DeadlockSnapshot(PreventionMode prevention, DeadlockReport current, long detected, long resolved) {
}
