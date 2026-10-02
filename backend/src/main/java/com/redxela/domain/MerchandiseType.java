package com.redxela.domain;

import static com.redxela.domain.ResourceType.FORKLIFT;
import static com.redxela.domain.ResourceType.LOADING_DOCK;
import static com.redxela.domain.ResourceType.PACKING_STATION;
import static com.redxela.domain.ResourceType.QUALITY_STATION;
import static com.redxela.domain.ResourceType.SCANNER;

import java.util.List;

/**
 * Tipo de mercancía. Cada tipo declara la combinación de recursos que necesita (receta), sin
 * contar al encargado de bodega, que se asigna al admitir cualquier pedido.
 *
 * <p>Restricciones de diseño que se verifican en los tests:
 * <ul>
 *   <li>Cada tipo requiere una combinación <b>distinta</b> de recursos.</li>
 *   <li>Cada receta ya está escrita en el orden global de {@link ResourceType#rank()}.</li>
 *   <li>{@link #HEAVY} y {@link #BULKY} se disputan montacargas y estación de empaque
 *       (escenario de contención entre dos recursos compartidos).</li>
 * </ul>
 */
public enum MerchandiseType {

    STANDARD("Estándar", "Ropa, libros y accesorios sin requisitos especiales.",
            PACKING_STATION, SCANNER, LOADING_DOCK),

    FRAGILE("Frágil", "Cristalería y cerámica: requiere inspección antes de empacar.",
            QUALITY_STATION, PACKING_STATION, SCANNER, LOADING_DOCK),

    HEAVY("Pesado", "Electrodomésticos y muebles: requiere montacargas para moverlos.",
            FORKLIFT, PACKING_STATION, SCANNER, LOADING_DOCK),

    PERISHABLE("Perecedero", "Alimentos y medicinas: llegan empacados, van directo a despacho.",
            QUALITY_STATION, SCANNER, LOADING_DOCK),

    BULKY("Voluminoso", "Colchones y bicicletas: montacargas e inspección de calidad.",
            FORKLIFT, QUALITY_STATION, PACKING_STATION, SCANNER, LOADING_DOCK);

    private final String displayName;
    private final String description;
    private final List<ResourceType> recipe;

    MerchandiseType(String displayName, String description, ResourceType... recipe) {
        this.displayName = displayName;
        this.description = description;
        this.recipe = List.of(recipe);
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    /** Recursos que puede requerir este tipo, en orden global de adquisición. */
    public List<ResourceType> recipe() {
        return recipe;
    }
}
