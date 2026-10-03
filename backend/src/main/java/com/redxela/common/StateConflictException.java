package com.redxela.common;

/**
 * La acción pedida no corresponde al estado actual del sistema (por ejemplo, resolver un deadlock que ya
 * no existe, lanzar el escenario con el sistema ocupado o iniciar una comparación que ya está corriendo).
 * La interfaz la recibe como un 409.
 */
public class StateConflictException extends RuntimeException {

    public StateConflictException(String message) {
        super(message);
    }
}
