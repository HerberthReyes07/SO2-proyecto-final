package com.redxela.generator;

import com.redxela.domain.ServiceLevel;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Forma de la carga que genera el sistema: qué mezcla de niveles de servicio llega y qué tan grandes
 * son los pedidos. Es independiente de la intensidad (cuántos pedidos por minuto), que se ajusta
 * en caliente desde la interfaz.
 *
 * @param levelWeights      peso relativo de cada nivel de servicio en la mezcla
 * @param lineCountWeights  peso de que un pedido tenga 1, 2, 3... líneas (la posición 0 es "1 línea")
 * @param meanExtraUnits    promedio de unidades por línea por encima de la primera (distribución exponencial)
 * @param maxUnitsPerLine   tope de unidades por línea
 */
public record WorkloadProfile(
        Map<ServiceLevel, Integer> levelWeights,
        List<Integer> lineCountWeights,
        double meanExtraUnits,
        int maxUnitsPerLine) {

    public WorkloadProfile {
        levelWeights = new EnumMap<>(levelWeights);
        lineCountWeights = List.copyOf(lineCountWeights);
        if (levelWeights.values().stream().noneMatch(weight -> weight > 0)) {
            throw new IllegalArgumentException("At least one service level must have a positive weight");
        }
        if (lineCountWeights.isEmpty() || lineCountWeights.stream().noneMatch(weight -> weight > 0)) {
            throw new IllegalArgumentException("At least one line count must have a positive weight");
        }
        if (meanExtraUnits < 0 || maxUnitsPerLine < 1) {
            throw new IllegalArgumentException("Unit settings out of range");
        }
    }

    /**
     * Mezcla por defecto: pocos pedidos Exprés y muchos de nivel medio, como en un comercio real.
     * Con estos valores un pedido tiene en promedio unos 8 artículos.
     */
    public static WorkloadProfile defaults() {
        return new WorkloadProfile(
                Map.of(ServiceLevel.EXPRESS, 5,
                        ServiceLevel.PRIORITY, 15,
                        ServiceLevel.STANDARD, 35,
                        ServiceLevel.SCHEDULED, 25,
                        ServiceLevel.ECONOMY, 20),
                List.of(70, 20, 10),
                5.0,
                30);
    }
}
