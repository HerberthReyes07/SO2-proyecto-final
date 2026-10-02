package com.redxela.api;

import com.redxela.scheduling.SchedulingPolicy;

/** Política de atención tal como la ve la interfaz. */
public record PolicyInfo(String id, String displayName, String description) {

    static PolicyInfo from(SchedulingPolicy policy) {
        return new PolicyInfo(policy.name(), policy.displayName(), policy.description());
    }
}
