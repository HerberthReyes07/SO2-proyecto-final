package com.redxela.api;

import com.redxela.domain.Order;
import com.redxela.orders.OrderService;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

/** Rutas de pedidos: registro manual de un pedido. */
public final class OrderRoutes implements ApiRoutes {

    private final OrderService orders;

    public OrderRoutes(OrderService orders) {
        this.orders = orders;
    }

    @Override
    public void register(Javalin app) {
        app.post("/api/orders", this::place);
    }

    private void place(Context ctx) {
        PlaceOrderRequest request = ctx.bodyValidator(PlaceOrderRequest.class).get();
        Order order = orders.place(request.customerId(), request.lines());
        ctx.status(HttpStatus.CREATED).json(new OrderConfirmation(order.id(), order.totalItems()));
    }
}
