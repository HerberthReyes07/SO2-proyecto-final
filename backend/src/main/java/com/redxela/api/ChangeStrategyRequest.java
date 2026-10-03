package com.redxela.api;

import com.redxela.warehouse.PlacementStrategy;

/** Cuerpo de {@code PUT /api/warehouse/strategy}. */
public record ChangeStrategyRequest(PlacementStrategy strategy) {
}
