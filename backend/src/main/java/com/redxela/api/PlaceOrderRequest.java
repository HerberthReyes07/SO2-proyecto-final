package com.redxela.api;

import com.redxela.orders.OrderLineRequest;
import java.util.List;

/** Cuerpo de {@code POST /api/orders}. */
public record PlaceOrderRequest(String customerId, List<OrderLineRequest> lines) {
}
