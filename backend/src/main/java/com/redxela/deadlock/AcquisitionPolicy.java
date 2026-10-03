package com.redxela.deadlock;

import com.redxela.domain.ResourceType;
import java.util.List;
import java.util.Objects;

/**
 * Política vigente de prevención de deadlock: las operaciones le preguntan en qué orden deben
 * pedir sus recursos. Se puede cambiar en caliente.
 *
 * <p>Por defecto la prevención está <b>activa</b>: así el sistema no puede congelarse por accidente;
 * el deadlock solo ocurre cuando el usuario lo habilita a propósito.
 */
public final class AcquisitionPolicy {

    public static final PreventionMode DEFAULT_MODE = PreventionMode.GLOBAL_ORDER;

    // Un único campo que se lee desde muchos hilos y se escribe rara vez: volatile basta.
    private volatile PreventionMode mode = DEFAULT_MODE;

    public PreventionMode mode() {
        return mode;
    }

    public void setMode(PreventionMode newMode) {
        this.mode = Objects.requireNonNull(newMode, "newMode");
    }

    /** El orden en que una operación debe pedir {@code natural} bajo la política vigente. */
    public List<ResourceType> arrange(List<ResourceType> natural) {
        return mode.arrange(natural);
    }
}
