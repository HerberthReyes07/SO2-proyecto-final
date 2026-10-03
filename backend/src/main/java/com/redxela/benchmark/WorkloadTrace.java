package com.redxela.benchmark;

import com.redxela.catalog.ProductCatalog;
import com.redxela.domain.Customer;
import com.redxela.generator.WorkloadGenerator;
import com.redxela.generator.WorkloadProfile;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * La carga de un experimento como una lista fija de llegadas: qué pedido llega y cuándo.
 *
 * <p>Comparar políticas exige que todas reciban <b>exactamente la misma carga</b>. El generador en vivo
 * decide cuándo llega cada pedido con el reloj y los hilos, y eso varía un poco entre corridas; aquí, en
 * cambio, tanto los pedidos como sus instantes de llegada se calculan de antemano a partir de la semilla,
 * así que cada política recibe una entrada idéntica.
 *
 * <p>Usa {@link WorkloadGenerator} en el mismo orden que el generador en vivo (primero el tiempo hasta la
 * próxima llegada, luego el pedido), de modo que con la misma semilla produce los mismos pedidos.
 */
public final class WorkloadTrace {

    private static final double MILLIS_PER_MINUTE = 60_000.0;

    private WorkloadTrace() {
    }

    /**
     * @param customers clientes entre los que se reparten los pedidos
     * @return las llegadas, en orden cronológico
     */
    public static List<TraceEntry> generate(WorkloadProfile profile, ProductCatalog catalog, List<Customer> customers,
                                            BenchmarkParams params) {
        WorkloadGenerator generator = new WorkloadGenerator(profile, catalog, params.seed());
        List<TraceEntry> trace = new ArrayList<>(params.orders());
        double minutes = 0;
        for (int i = 0; i < params.orders(); i++) {
            minutes += generator.nextArrivalWork() / params.ordersPerMinute();
            Duration arrivalAt = Duration.ofMillis(Math.round(minutes * MILLIS_PER_MINUTE));
            trace.add(new TraceEntry(arrivalAt, generator.nextOrder(customers)));
        }
        return List.copyOf(trace);
    }
}
