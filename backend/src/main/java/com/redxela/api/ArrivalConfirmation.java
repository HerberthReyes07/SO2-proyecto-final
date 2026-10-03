package com.redxela.api;

import java.util.List;

/** Respuesta de {@code POST /api/receptions}: los camiones que quedaron en Recepción. */
public record ArrivalConfirmation(List<String> truckIds) {
}
