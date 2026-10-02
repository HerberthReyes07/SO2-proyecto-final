package com.redxela.api;

import com.redxela.customers.CustomerRegistry;
import com.redxela.domain.Customer;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

/** Rutas de cuentas de cliente: listar y crear (un cliente nuevo elige su nivel de servicio). */
public final class CustomerRoutes implements ApiRoutes {

    private static final String PATH = "/api/customers";

    private final CustomerRegistry customers;

    public CustomerRoutes(CustomerRegistry customers) {
        this.customers = customers;
    }

    @Override
    public void register(Javalin app) {
        app.get(PATH, ctx -> ctx.json(customers.all()));
        app.post(PATH, this::create);
    }

    private void create(Context ctx) {
        CreateCustomerRequest request = ctx.bodyValidator(CreateCustomerRequest.class).get();
        Customer customer = customers.create(request.name(), request.serviceLevel());
        ctx.status(HttpStatus.CREATED).json(customer);
    }
}
