package com.redxela.api;

import com.redxela.deadlock.PreventionMode;

/** Cuerpo de {@code PUT /api/deadlock/prevention}. */
public record ChangePreventionRequest(PreventionMode mode) {
}
