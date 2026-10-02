package com.redxela.api;

/**
 * Cuerpo de {@code PATCH /api/clock}. Es una actualización parcial: los campos ausentes (null)
 * no se modifican.
 *
 * @param speed  nuevo factor de aceleración, o null para dejarlo como está
 * @param paused {@code true} para pausar, {@code false} para reanudar, null para no tocar la pausa
 */
public record ClockUpdateRequest(Double speed, Boolean paused) {
}
