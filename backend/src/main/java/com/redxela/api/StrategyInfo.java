package com.redxela.api;

import com.redxela.warehouse.PlacementStrategy;

/** Estrategia de ubicación en el almacén tal como la ve la interfaz. */
public record StrategyInfo(String id, String displayName, String description) {

    static StrategyInfo from(PlacementStrategy strategy) {
        return new StrategyInfo(strategy.name(), strategy.displayName(), strategy.description());
    }
}
