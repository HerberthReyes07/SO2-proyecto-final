package com.redxela.generator;

import com.redxela.orders.OrderLineRequest;
import java.util.List;

/** Lo que el generador necesita del sistema de pedidos: poder registrar uno. */
@FunctionalInterface
public interface OrderPlacer {

    void place(String customerId, List<OrderLineRequest> lines);
}
