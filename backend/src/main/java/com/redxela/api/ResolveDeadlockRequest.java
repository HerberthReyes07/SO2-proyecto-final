package com.redxela.api;

/**
 * Cuerpo de {@code POST /api/deadlock/resolve}.
 *
 * @param continueOperationId operación en conflicto que conserva los recursos; la otra vuelve a la lista de espera
 */
public record ResolveDeadlockRequest(String continueOperationId) {
}
