package com.redxela.api;

import com.redxela.domain.Product;
import java.util.List;

/**
 * Datos de referencia que la interfaz necesita para armar formularios. Se piden una sola vez
 * porque no cambian durante la ejecución.
 *
 * @param products         productos del catálogo
 * @param merchandiseTypes tipos de mercancía con su descripción y receta de recursos
 * @param serviceLevels    niveles de servicio con su tiempo objetivo
 * @param policies         políticas de atención disponibles, con la explicación de su criterio
 * @param placementStrategies estrategias para elegir ubicaciones en el almacén, con su explicación
 */
public record CatalogResponse(
        List<Product> products,
        List<MerchandiseTypeInfo> merchandiseTypes,
        List<ServiceLevelInfo> serviceLevels,
        List<PolicyInfo> policies,
        List<StrategyInfo> placementStrategies) {
}
