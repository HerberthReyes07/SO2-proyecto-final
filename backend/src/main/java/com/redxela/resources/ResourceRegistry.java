package com.redxela.resources;

import com.redxela.domain.ResourceType;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Catálogo de pools del centro de distribución: uno por cada {@link ResourceType}.
 *
 * <p>Los pools se crean una sola vez al construir el registro, así que el mapa es de solo lectura
 * después y puede compartirse entre hilos sin sincronización adicional.
 */
public final class ResourceRegistry {

    private final Map<ResourceType, ResourcePool> pools = new EnumMap<>(ResourceType.class);

    public ResourceRegistry() {
        for (ResourceType type : ResourceType.values()) {
            pools.put(type, new ResourcePool(type));
        }
    }

    public ResourcePool pool(ResourceType type) {
        return pools.get(type);
    }

    /** Fotos de todos los pools, en orden global de recursos. */
    public List<PoolSnapshot> snapshots() {
        return Arrays.stream(ResourceType.values())
                .map(type -> pools.get(type).snapshot())
                .toList();
    }
}
