package com.redxela.resources;

/**
 * Unidad de trabajo que compite por recursos: el procesamiento de un pedido o la recepción de un
 * camión. Los pools solo necesitan identificarla; no conocen el dominio de quien los usa.
 *
 * <p>La igualdad es por identidad (comportamiento por defecto de {@code Object}): dos operaciones
 * distintas nunca se confunden aunque compartan id.
 */
public interface Operation {

    /** Identificador legible, usado en la interfaz y en los logs (ej. "ORD-0042", "TRK-0007"). */
    String id();

    /** Clase de operación; por defecto {@link OperationKind#OTHER}. */
    default OperationKind kind() {
        return OperationKind.OTHER;
    }
}
