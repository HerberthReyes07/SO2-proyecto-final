package com.redxela.sim;

/**
 * Foto consistente del reloj simulado, tomada bajo su lock.
 *
 * @param simulatedMillis milisegundos simulados transcurridos desde el inicio de la simulación
 * @param speed           factor de aceleración (60 = 1 segundo real equivale a 1 minuto simulado)
 * @param paused          {@code true} si el tiempo simulado está detenido
 */
public record ClockSnapshot(long simulatedMillis, double speed, boolean paused) {
}
