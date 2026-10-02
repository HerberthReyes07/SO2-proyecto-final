package com.redxela.api;

import io.javalin.Javalin;

/**
 * Grupo de rutas HTTP de un área de la aplicación (reloj, pedidos, inventario...). Cada área
 * registra sus propias rutas, así {@link ApiServer} no crece con cada funcionalidad nueva.
 */
public interface ApiRoutes {

    void register(Javalin app);
}
