package com.redxela.processing;

import com.redxela.domain.Order;
import com.redxela.domain.ResourceType;
import com.redxela.domain.Stage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Plan completo de un pedido: las etapas que recorrerá, con los recursos y el trabajo de cada una.
 * Se calcula una sola vez al recibir el pedido, así el tiempo total estimado se conoce de antemano.
 *
 * @param stages etapas en orden de ejecución
 */
public record ExecutionPlan(List<StagePlan> stages) {

    public ExecutionPlan {
        stages = List.copyOf(stages);
    }

    /**
     * Construye el plan de un pedido. La etapa de picking siempre existe (el encargado siempre
     * recoge la mercancía); las demás solo aparecen si la receta del pedido usa algún recurso de ella.
     */
    public static ExecutionPlan forOrder(Order order, ProcessingTimes times) {
        List<ResourceType> recipe = order.requiredResources();
        List<StagePlan> stages = new ArrayList<>();
        for (Stage stage : Stage.values()) {
            List<ResourceType> stageResources = recipe.stream()
                    .filter(resource -> resource.stage() == stage)
                    .toList();
            if (stage == Stage.PICKING || !stageResources.isEmpty()) {
                Duration work = times.perUnit(stage).multipliedBy(order.totalItems());
                stages.add(new StagePlan(stage, stageResources, work));
            }
        }
        return new ExecutionPlan(stages);
    }

    /** Suma del trabajo de todas las etapas (no incluye tiempos de espera por recursos). */
    public Duration totalWork() {
        return stages.stream().map(StagePlan::work).reduce(Duration.ZERO, Duration::plus);
    }
}
