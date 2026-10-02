package com.redxela.processing;

import com.redxela.domain.ResourceType;
import com.redxela.domain.Stage;
import java.time.Duration;
import java.util.List;

/**
 * Una etapa del plan de un pedido.
 *
 * @param stage     etapa
 * @param resources recursos que debe adquirir en esta etapa, en orden global (sin el encargado)
 * @param work      tiempo simulado de trabajo en la etapa, ya multiplicado por las unidades del pedido
 */
public record StagePlan(Stage stage, List<ResourceType> resources, Duration work) {

    public StagePlan {
        resources = List.copyOf(resources);
    }
}
