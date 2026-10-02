package com.redxela.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.redxela.domain.MerchandiseType;
import com.redxela.domain.Product;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ProductCatalogTest {

    private final ProductCatalog catalog = ProductCatalog.defaultCatalog();

    @Test
    void has_at_least_fifteen_products() {
        assertThat(catalog.all()).hasSizeGreaterThanOrEqualTo(15);
    }

    @Test
    void covers_every_merchandise_type() {
        Set<MerchandiseType> covered = catalog.all().stream().map(Product::type).collect(Collectors.toSet());

        assertThat(covered).containsExactlyInAnyOrder(MerchandiseType.values());
    }

    @Test
    void product_ids_are_unique() {
        List<String> ids = catalog.all().stream().map(Product::id).toList();

        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void every_product_fits_in_one_warehouse_location() {
        // La ubicación del almacén (paso 6) será de 100 unidades de volumen; una unidad debe caber.
        assertThat(catalog.all()).allSatisfy(product -> assertThat(product.unitVolume()).isLessThanOrEqualTo(100));
    }

    @Test
    void finds_a_product_by_id() {
        assertThat(catalog.findById("P-007")).map(Product::name).contains("Refrigeradora 300 L");
        assertThat(catalog.findById("nope")).isEmpty();
    }
}
