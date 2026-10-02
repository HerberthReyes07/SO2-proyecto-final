package com.redxela.generator;

import com.redxela.catalog.ProductCatalog;
import com.redxela.domain.Customer;
import com.redxela.domain.Product;
import com.redxela.domain.ServiceLevel;
import com.redxela.orders.OrderLineRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Decide <b>qué</b> pedido llega y <b>cuánto trabajo de espera</b> hay hasta el siguiente, de forma
 * determinista a partir de una semilla: la misma semilla produce siempre la misma secuencia.
 *
 * <p>Que sea determinista y no sepa nada de hilos ni de relojes es lo que permite repetir un
 * experimento (por ejemplo, correr cada política de atención con exactamente la misma carga).
 * Cuándo llega cada pedido lo decide quien lo use; ver {@link OrderGenerator}.
 *
 * <p>No es seguro entre hilos: lo usa un único hilo.
 */
public final class WorkloadGenerator {

    /** Pedido que debe crearse: quién lo pide y qué productos. */
    public record OrderSpec(String customerId, List<OrderLineRequest> lines) {
    }

    private final WorkloadProfile profile;
    private final ProductCatalog catalog;
    private final Random random;

    public WorkloadGenerator(WorkloadProfile profile, ProductCatalog catalog, long seed) {
        this.profile = profile;
        this.catalog = catalog;
        this.random = new Random(seed);
    }

    /**
     * Cantidad de "trabajo" hasta la próxima llegada: una variable exponencial de media 1.
     * Dividida entre la intensidad (pedidos por minuto) da el tiempo de espera en minutos; dejarla
     * sin dividir permite que la intensidad cambie mientras se espera (ver {@link OrderGenerator}).
     */
    public double nextArrivalWork() {
        // 1 - u está en (0, 1], así el logaritmo nunca recibe cero.
        return -Math.log(1.0 - random.nextDouble());
    }

    /**
     * Genera el siguiente pedido: elige un nivel según la mezcla, un cliente de ese nivel y los productos.
     *
     * @param customers clientes existentes en este momento; si ninguno tiene el nivel elegido se usa cualquiera
     * @throws IllegalArgumentException si no hay clientes
     */
    public OrderSpec nextOrder(List<Customer> customers) {
        if (customers.isEmpty()) {
            throw new IllegalArgumentException("There are no customers to generate orders for");
        }
        Customer customer = pickCustomer(customers);
        return new OrderSpec(customer.id(), pickLines());
    }

    private Customer pickCustomer(List<Customer> customers) {
        ServiceLevel level = pickLevel();
        List<Customer> ofLevel = customers.stream().filter(customer -> customer.level() == level).toList();
        List<Customer> candidates = ofLevel.isEmpty() ? customers : ofLevel;
        return candidates.get(random.nextInt(candidates.size()));
    }

    private ServiceLevel pickLevel() {
        ServiceLevel[] levels = ServiceLevel.values();
        int[] weights = new int[levels.length];
        for (int i = 0; i < levels.length; i++) {
            weights[i] = profile.levelWeights().getOrDefault(levels[i], 0);
        }
        return levels[pickWeightedIndex(weights)];
    }

    private List<OrderLineRequest> pickLines() {
        int lineCount = 1 + pickWeightedIndex(profile.lineCountWeights().stream().mapToInt(Integer::intValue).toArray());
        List<Product> shuffled = new ArrayList<>(catalog.all());
        Collections.shuffle(shuffled, random);

        List<OrderLineRequest> lines = new ArrayList<>();
        for (Product product : shuffled.subList(0, Math.min(lineCount, shuffled.size()))) {
            lines.add(new OrderLineRequest(product.id(), pickUnits()));
        }
        return lines;
    }

    /** 1 + una variable exponencial: muchos pedidos pequeños y algunos grandes, con tope. */
    private int pickUnits() {
        double extra = -Math.log(1.0 - random.nextDouble()) * profile.meanExtraUnits();
        return (int) Math.min(profile.maxUnitsPerLine(), 1 + Math.floor(extra));
    }

    /** Devuelve un índice con probabilidad proporcional a su peso. */
    private int pickWeightedIndex(int[] weights) {
        int total = 0;
        for (int weight : weights) {
            total += Math.max(0, weight);
        }
        int roll = random.nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            roll -= Math.max(0, weights[i]);
            if (roll < 0) {
                return i;
            }
        }
        throw new IllegalStateException("Unreachable: the roll is always smaller than the total weight");
    }
}
