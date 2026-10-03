package com.redxela.deadlock;

import com.redxela.domain.ResourceType;
import java.util.Comparator;
import java.util.List;

/**
 * Estrategia de prevención de deadlock: decide en qué orden una operación pide los recursos que
 * necesita.
 *
 * <p>Un deadlock exige las cuatro condiciones de Coffman a la vez. Los recursos no compartibles
 * (exclusión mutua) y la imposibilidad de quitárselos a quien los tiene (sin apropiación) son
 * inherentes a escáner y áreas de carga, y retener uno mientras se espera otro (retención y
 * espera) es natural en el trabajo. Lo que sí se puede evitar es la <b>espera circular</b>:
 * si todas las operaciones piden sus recursos en el mismo orden global, nunca puede formarse un ciclo.
 */
public enum PreventionMode {

    NONE("Sin prevención",
            "Cada operación pide los recursos en su orden natural: los pedidos escáner → área de carga y los "
                    + "camiones área de carga → escáner (el orden inverso). Puede producirse un deadlock, que el "
                    + "detector reconoce y que se resuelve a mano.") {
        @Override
        public List<ResourceType> arrange(List<ResourceType> natural) {
            return List.copyOf(natural);
        }
    },

    GLOBAL_ORDER("Orden global de recursos",
            "Todas las operaciones piden los recursos en el mismo orden (el de la lista de recursos: escáner antes "
                    + "que área de carga). Rompe la espera circular, así que el deadlock es imposible; el costo es "
                    + "que un camión retiene el escáner un rato más mientras acomoda el camión.") {
        @Override
        public List<ResourceType> arrange(List<ResourceType> natural) {
            return natural.stream().sorted(Comparator.comparingInt(ResourceType::rank)).toList();
        }
    };

    private final String displayName;
    private final String description;

    PreventionMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    /** Explicación de la estrategia, pensada para mostrarse al usuario. */
    public String description() {
        return description;
    }

    /** Reordena los recursos que una operación necesita, que es el orden en que los pedirá. */
    public abstract List<ResourceType> arrange(List<ResourceType> natural);
}
