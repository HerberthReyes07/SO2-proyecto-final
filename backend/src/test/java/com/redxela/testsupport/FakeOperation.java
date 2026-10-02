package com.redxela.testsupport;

import com.redxela.resources.Operation;

/** Operación mínima para ocupar unidades de un pool en los tests. */
public final class FakeOperation implements Operation {

    private final String id;

    public FakeOperation(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }
}
