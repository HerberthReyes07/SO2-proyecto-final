package com.redxela.resources;

import com.redxela.domain.ResourceType;

/**
 * Se lanza en el hilo de una operación que esperaba un recurso y fue elegida como víctima de la
 * resolución de un deadlock. La operación debe liberar todo lo que retiene y volver a la lista de
 * espera. Es una excepción <i>checked</i> a propósito: obliga a quien adquiere recursos a decidir
 * cómo deshacer su trabajo.
 */
public class OperationPreemptedException extends Exception {

    private final String operationId;
    private final ResourceType awaitedResource;

    public OperationPreemptedException(String operationId, ResourceType awaitedResource) {
        super("Operation " + operationId + " was preempted while waiting for " + awaitedResource);
        this.operationId = operationId;
        this.awaitedResource = awaitedResource;
    }

    public String operationId() {
        return operationId;
    }

    public ResourceType awaitedResource() {
        return awaitedResource;
    }
}
