package com.redxela.demo;

import com.redxela.deadlock.PreventionMode;

/**
 * Estado del escenario de demostración para la interfaz.
 *
 * @param phase en qué punto está
 * @param mode  prevención con la que se lanzó el último escenario, o null si nunca se lanzó
 */
public record ScenarioSnapshot(ScenarioPhase phase, PreventionMode mode) {
}
