package com.redxela.deadlock;

/**
 * Las dos operaciones en conflicto: {@code waiter} espera un recurso que retiene {@code holder}.
 * Para resolverlo se elige cuál de las dos continúa; la otra vuelve a la lista de espera.
 *
 * @param waiter operación que espera el recurso
 * @param holder operación que retiene el recurso que {@code waiter} espera
 */
public record ConflictPair(String waiter, String holder) {
}
