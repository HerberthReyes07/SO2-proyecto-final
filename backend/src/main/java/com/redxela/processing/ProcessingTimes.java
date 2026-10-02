package com.redxela.processing;

import com.redxela.domain.Stage;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * Tiempo simulado de trabajo <b>por unidad</b> en cada etapa. El enunciado indica que la cantidad
 * de unidades del pedido es la estimación base del trabajo: más unidades, más tiempo reteniendo recursos.
 */
public final class ProcessingTimes {

    private final Map<Stage, Duration> perUnit;

    public ProcessingTimes(Map<Stage, Duration> perUnit) {
        for (Stage stage : Stage.values()) {
            if (!perUnit.containsKey(stage)) {
                throw new IllegalArgumentException("Missing processing time for stage " + stage);
            }
        }
        this.perUnit = new EnumMap<>(perUnit);
    }

    /**
     * Valores por defecto: con la simulación a ×60, un pedido estándar de 10 unidades tarda unos
     * 5 segundos reales, suficiente para verlo avanzar sin esperar demasiado.
     */
    public static ProcessingTimes defaults() {
        return new ProcessingTimes(Map.of(
                Stage.PICKING, Duration.ofSeconds(8),
                Stage.INSPECTION, Duration.ofSeconds(15),
                Stage.PACKING, Duration.ofSeconds(20),
                Stage.DISPATCH, Duration.ofSeconds(5)));
    }

    public Duration perUnit(Stage stage) {
        return perUnit.get(stage);
    }
}
