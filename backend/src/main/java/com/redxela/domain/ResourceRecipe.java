package com.redxela.domain;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Calcula la receta de recursos de un pedido, que puede mezclar varios tipos de mercancía.
 *
 * <p>La receta es la <b>unión</b> de las recetas de cada tipo, ordenada según el orden global
 * de {@link ResourceType}. Como todas las recetas individuales respetan ese orden, la unión
 * también está bien definida y no introduce adquisiciones en orden inverso.
 */
public final class ResourceRecipe {

    private ResourceRecipe() {
    }

    /**
     * @param types tipos de mercancía presentes en el pedido (no vacío)
     * @return recursos distintos a adquirir, en orden ascendente de rank
     */
    public static List<ResourceType> forTypes(Collection<MerchandiseType> types) {
        if (types == null || types.isEmpty()) {
            throw new IllegalArgumentException("An order must contain at least one merchandise type");
        }
        return types.stream()
                .flatMap(type -> type.recipe().stream())
                .distinct()
                .sorted(Comparator.comparingInt(ResourceType::rank))
                .toList();
    }
}
