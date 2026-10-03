package com.redxela.api;

import com.redxela.deadlock.PreventionMode;
import com.redxela.scheduling.SchedulingPolicy;
import com.redxela.warehouse.PlacementStrategy;

/**
 * Una opción elegible con su explicación, tal como la ve la interfaz: sirve para las políticas de
 * atención, las estrategias de ubicación y los modos de prevención de deadlock.
 */
public record OptionInfo(String id, String displayName, String description) {

    static OptionInfo from(SchedulingPolicy policy) {
        return new OptionInfo(policy.name(), policy.displayName(), policy.description());
    }

    static OptionInfo from(PlacementStrategy strategy) {
        return new OptionInfo(strategy.name(), strategy.displayName(), strategy.description());
    }

    static OptionInfo from(PreventionMode mode) {
        return new OptionInfo(mode.name(), mode.displayName(), mode.description());
    }
}
