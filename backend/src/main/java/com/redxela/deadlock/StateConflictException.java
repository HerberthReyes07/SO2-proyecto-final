package com.redxela.deadlock;

/**
 * La acción pedida ya no corresponde al estado actual del sistema (por ejemplo, resolver un
 * deadlock que ya no existe, o lanzar el escenario con el sistema ocupado). La interfaz la recibe como un 409.
 */
public class StateConflictException extends RuntimeException {

    public StateConflictException(String message) {
        super(message);
    }
}
