package com.redxela.api;

import com.redxela.catalog.ProductCatalog;
import com.redxela.domain.MerchandiseType;
import com.redxela.domain.ServiceLevel;
import io.javalin.Javalin;
import java.util.Arrays;

/** Datos de referencia de solo lectura: productos, tipos de mercancía y niveles de servicio. */
public final class CatalogRoutes implements ApiRoutes {

    private final CatalogResponse response;

    public CatalogRoutes(ProductCatalog catalog) {
        // Los datos son inmutables, así que la respuesta se arma una sola vez.
        this.response = new CatalogResponse(
                catalog.all(),
                Arrays.stream(MerchandiseType.values()).map(MerchandiseTypeInfo::from).toList(),
                Arrays.stream(ServiceLevel.values()).map(ServiceLevelInfo::from).toList());
    }

    @Override
    public void register(Javalin app) {
        app.get("/api/catalog", ctx -> ctx.json(response));
    }
}
