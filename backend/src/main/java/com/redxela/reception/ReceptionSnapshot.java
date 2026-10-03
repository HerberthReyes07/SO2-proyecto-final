package com.redxela.reception;

import java.util.List;

/**
 * Estado del área de Recepción para la interfaz.
 *
 * @param trucks camiones que todavía no terminaron de reportar su mercadería, en orden de llegada
 */
public record ReceptionSnapshot(List<TruckProgressSnapshot> trucks) {

    public ReceptionSnapshot {
        trucks = List.copyOf(trucks);
    }
}
