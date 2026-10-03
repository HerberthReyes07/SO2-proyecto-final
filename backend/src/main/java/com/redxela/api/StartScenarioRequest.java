package com.redxela.api;

import com.redxela.deadlock.PreventionMode;

/**
 * Cuerpo de {@code POST /api/deadlock/scenario}.
 *
 * @param mode estrategia de prevención con la que correr el escenario; ausente (null) equivale a
 *             {@code NONE}, que es la que provoca el deadlock
 */
public record StartScenarioRequest(PreventionMode mode) {
}
