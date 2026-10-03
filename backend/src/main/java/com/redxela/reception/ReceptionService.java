package com.redxela.reception;

import com.redxela.catalog.ProductCatalog;
import com.redxela.domain.ResourceType;
import com.redxela.inventory.ReceptionBuffer;
import com.redxela.inventory.ReceptionMessage;
import com.redxela.resources.OperationPreemptedException;
import com.redxela.resources.ResourceRegistry;
import com.redxela.sim.SimClock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Área de Recepción de Mercadería: cada camión que llega es una operación (un hilo) que toma el
 * escáner y un andén de carga, descarga y registra la mercadería, los suelta y luego informa a
 * Inventario enviando un mensaje por el buffer.
 *
 * <h3>Soltar los recursos antes de reportar</h3>
 * El camión libera escáner y andén <b>antes</b> de poner su mensaje en el buffer. Si los retuviera
 * mientras espera espacio, un Inventario lento dejaría el único escáner ocupado por camiones que ya
 * terminaron de descargar y paralizaría los despachos. Así, un buffer lleno solo hace esperar al
 * camión que reporta.
 *
 * <h3>Orden de adquisición</h3>
 * Los recursos se piden en el orden global ({@link #RECIPE}), igual que los pedidos, por lo que no
 * pueden formar espera circular con ellos. El orden inverso, que provoca el deadlock de la
 * demostración, se introduce junto con su detección y resolución.
 */
public final class ReceptionService implements AutoCloseable {

    /** Recursos que necesita un camión, en el orden global de adquisición. */
    static final List<ResourceType> RECIPE = List.of(ResourceType.SCANNER, ResourceType.LOADING_DOCK);

    public static final int MAX_QUANTITY_PER_TRUCK = 1_000;
    public static final int MAX_TRUCKS_PER_REQUEST = 50;
    /** Tope de camiones en el sistema, para que una ráfaga no cree hilos sin límite. */
    public static final int MAX_PENDING_TRUCKS = 100;

    /** Tiempo simulado de descarga y registro por cada unidad que trae el camión. */
    static final Duration UNLOAD_TIME_PER_UNIT = Duration.ofSeconds(1);

    private static final Logger LOG = LoggerFactory.getLogger(ReceptionService.class);

    private final ResourceRegistry resources;
    private final SimClock clock;
    private final ReceptionBuffer buffer;
    private final ProductCatalog catalog;
    private final ExecutorService threads =
            Executors.newThreadPerTaskExecutor(Thread.ofPlatform().name("truck-", 1).factory());
    private final Map<String, TruckExecution> active = new ConcurrentHashMap<>();
    private final AtomicInteger nextNumber = new AtomicInteger();

    public ReceptionService(ResourceRegistry resources, SimClock clock, ReceptionBuffer buffer, ProductCatalog catalog) {
        this.resources = resources;
        this.clock = clock;
        this.buffer = buffer;
        this.catalog = catalog;
    }

    /**
     * Registra la llegada de uno o más camiones con la misma mercadería; cada uno se procesa en su hilo.
     *
     * @param trucks cantidad de camiones iguales (permite provocar una ráfaga que llene el buffer)
     * @return ids de los camiones creados
     * @throws IllegalArgumentException si el producto no existe, las cantidades están fuera de rango o hay demasiados camiones pendientes
     */
    public List<String> registerArrival(String productId, int quantity, int trucks) {
        if (catalog.findById(productId).isEmpty()) {
            throw new IllegalArgumentException("Unknown product: " + productId);
        }
        if (quantity < 1 || quantity > MAX_QUANTITY_PER_TRUCK) {
            throw new IllegalArgumentException("quantity must be between 1 and " + MAX_QUANTITY_PER_TRUCK);
        }
        if (trucks < 1 || trucks > MAX_TRUCKS_PER_REQUEST) {
            throw new IllegalArgumentException("trucks must be between 1 and " + MAX_TRUCKS_PER_REQUEST);
        }
        if (active.size() + trucks > MAX_PENDING_TRUCKS) {
            throw new IllegalArgumentException("Too many trucks pending (limit " + MAX_PENDING_TRUCKS + ")");
        }

        List<String> ids = new ArrayList<>();
        for (int i = 0; i < trucks; i++) {
            int number = nextNumber.incrementAndGet();
            TruckExecution truck = new TruckExecution(String.format("TRK-%04d", number), productId, quantity, number);
            active.put(truck.id(), truck);
            threads.execute(() -> runAndTrack(truck));
            ids.add(truck.id());
        }
        return ids;
    }

    /** Camiones que aún no terminaron de reportar, en orden de llegada. */
    public ReceptionSnapshot snapshot(Duration now) {
        return new ReceptionSnapshot(active.values().stream()
                .sorted(Comparator.comparingLong(TruckExecution::sequence))
                .map(truck -> truck.progress(now))
                .toList());
    }

    private void runAndTrack(TruckExecution truck) {
        try {
            unload(truck);
            truck.beginReporting();
            buffer.put(new ReceptionMessage(truck.id(), truck.productId(), truck.quantity()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // el servicio se está cerrando
            LOG.info("Truck {} interrupted", truck.id());
        } catch (OperationPreemptedException e) {
            // Aún no hay resolución de deadlock; cuando exista, el camión volverá a esperar su turno.
            LOG.warn("Truck {} was preempted: {}", truck.id(), e.getMessage());
        } catch (RuntimeException e) {
            LOG.error("Truck {} failed", truck.id(), e);
        } finally {
            active.remove(truck.id());
        }
    }

    /** Toma los recursos en orden, descarga y los suelta, pase lo que pase. */
    private void unload(TruckExecution truck) throws InterruptedException, OperationPreemptedException {
        Set<ResourceType> held = EnumSet.noneOf(ResourceType.class);
        try {
            for (ResourceType type : RECIPE) {
                truck.beginWaiting(type);
                resources.pool(type).acquire(truck);
                held.add(type);
                truck.resourceAcquired(type);
            }
            Duration work = UNLOAD_TIME_PER_UNIT.multipliedBy(truck.quantity());
            truck.beginUnloading(work, clock.now());
            clock.sleep(work);
        } finally {
            for (ResourceType type : List.copyOf(held)) {
                resources.pool(type).release(truck);
                truck.resourceReleased(type);
            }
        }
    }

    @Override
    public void close() {
        threads.shutdownNow();
    }
}
