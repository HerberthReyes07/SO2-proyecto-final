package com.redxela.catalog;

import static com.redxela.domain.MerchandiseType.BULKY;
import static com.redxela.domain.MerchandiseType.FRAGILE;
import static com.redxela.domain.MerchandiseType.HEAVY;
import static com.redxela.domain.MerchandiseType.PERISHABLE;
import static com.redxela.domain.MerchandiseType.STANDARD;

import com.redxela.domain.Product;
import java.util.List;
import java.util.Optional;

/**
 * Catálogo de productos de la simulación. Está predefinido: la interfaz solo permite elegir entre
 * estos productos, no crear nuevos. Es inmutable, por lo que se comparte entre hilos sin locks.
 */
public final class ProductCatalog {

    private final List<Product> products;

    public ProductCatalog(List<Product> products) {
        this.products = List.copyOf(products);
    }

    /** Catálogo por defecto: tres productos de cada tipo de mercancía. */
    public static ProductCatalog defaultCatalog() {
        return new ProductCatalog(List.of(
                new Product("P-001", "Camiseta de algodón", STANDARD, 2),
                new Product("P-002", "Novela en tapa blanda", STANDARD, 2),
                new Product("P-003", "Mochila urbana", STANDARD, 8),

                new Product("P-004", "Juego de copas de cristal", FRAGILE, 6),
                new Product("P-005", "Jarrón de cerámica", FRAGILE, 10),
                new Product("P-006", "Espejo decorativo", FRAGILE, 12),

                new Product("P-007", "Refrigeradora 300 L", HEAVY, 60),
                new Product("P-008", "Lavadora de carga frontal", HEAVY, 50),
                new Product("P-009", "Escritorio de madera", HEAVY, 40),

                new Product("P-010", "Caja de medicamentos", PERISHABLE, 1),
                new Product("P-011", "Fórmula láctea infantil", PERISHABLE, 3),
                new Product("P-012", "Chocolates artesanales", PERISHABLE, 2),

                new Product("P-013", "Colchón matrimonial", BULKY, 80),
                new Product("P-014", "Bicicleta de montaña", BULKY, 45),
                new Product("P-015", "Silla de oficina ergonómica", BULKY, 30)));
    }

    public List<Product> all() {
        return products;
    }

    public Optional<Product> findById(String id) {
        return products.stream().filter(product -> product.id().equals(id)).findFirst();
    }
}
