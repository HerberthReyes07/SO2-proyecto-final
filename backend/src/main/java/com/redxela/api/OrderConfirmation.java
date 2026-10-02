package com.redxela.api;

/** Respuesta de {@code POST /api/orders}. */
public record OrderConfirmation(String orderId, int totalItems) {
}
