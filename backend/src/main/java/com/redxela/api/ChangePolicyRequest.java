package com.redxela.api;

import com.redxela.scheduling.SchedulingPolicy;

/** Cuerpo de {@code PUT /api/scheduling/policy}. */
public record ChangePolicyRequest(SchedulingPolicy policy) {
}
