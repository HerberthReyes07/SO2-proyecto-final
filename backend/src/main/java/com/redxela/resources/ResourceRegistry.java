package com.redxela.resources;

import com.redxela.domain.ResourceType;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Catálogo de pools del centro de distribución: uno por cada {@link ResourceType}.
 *
 * <p>Los pools se crean una sola vez al construir el registro, así que el mapa es de solo lectura
 * después y puede compartirse entre hilos sin sincronización adicional.
 */
public final class ResourceRegistry {

    private final Map<ResourceType, ResourcePool> pools = new EnumMap<>(ResourceType.class);

    /** Registro que mide la ocupación con el reloj real (útil en pruebas aisladas). */
    public ResourceRegistry() {
        this(System::nanoTime);
    }

    /**
     * @param timeNanos fuente de tiempo para medir la ocupación; en la aplicación es el reloj
     *                  simulado, de modo que una pausa no cuenta como tiempo ocioso
     */
    public ResourceRegistry(LongSupplier timeNanos) {
        for (ResourceType type : ResourceType.values()) {
            pools.put(type, new ResourcePool(type, type.capacity(), (a, b) -> 0, timeNanos));
        }
    }

    public ResourcePool pool(ResourceType type) {
        return pools.get(type);
    }

    /** Cambia en todos los pools el criterio con que se elige a quién conceder el recurso. */
    public void setWaitingOrder(Comparator<Operation> waitingOrder) {
        pools.values().forEach(pool -> pool.setWaitingOrder(waitingOrder));
    }

    /** Reinicia la medición de ocupación de todos los pools. */
    public void resetUsage() {
        pools.values().forEach(ResourcePool::resetUsage);
    }

    /**
     * Quién retiene y quién espera cada recurso, en orden global. Cada pool se lee bajo su propio
     * lock, así que el conjunto <b>no es una foto atómica</b> de todo el sistema: quien lo use para
     * detectar deadlocks debe tenerlo en cuenta (ver {@code DeadlockDetector}).
     */
    public List<PoolOperations> operations() {
        return Arrays.stream(ResourceType.values()).map(type -> pools.get(type).operations()).toList();
    }

    /** Fotos de todos los pools, en orden global de recursos. */
    public List<PoolSnapshot> snapshots() {
        return Arrays.stream(ResourceType.values())
                .map(type -> pools.get(type).snapshot())
                .toList();
    }
}
