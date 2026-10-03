package com.redxela.warehouse;

import com.redxela.catalog.ProductCatalog;
import com.redxela.domain.Product;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * El almacén físico: qué ubicaciones están ocupadas y qué hay en cada una.
 *
 * <h3>Asignación no contigua, como la paginación</h3>
 * <ul>
 *   <li>Cada ubicación es un <b>marco</b> de tamaño fijo y pertenece a un único lote.</li>
 *   <li>Un lote es un proceso: su lista de ubicaciones es su <b>tabla de páginas</b>, y esas
 *       ubicaciones pueden estar en cualquier pasillo y nivel.</li>
 *   <li>Un <b>bitmap</b> ({@link BitSet}) marca qué ubicaciones están ocupadas.</li>
 *   <li>No hay fragmentación externa (cualquier ubicación libre sirve, esté donde esté); sí hay
 *       <b>fragmentación interna</b>: la última ubicación de un lote suele quedar a medias, y como
 *       una ubicación no se comparte entre lotes, ese espacio no se aprovecha.</li>
 * </ul>
 *
 * <h3>Una ubicación guarda unidades enteras de un producto</h3>
 * Cabe {@code capacidad / volumen de la unidad} unidades por ubicación, redondeado hacia abajo (un
 * colchón de 80 en una ubicación de 1.000 deja 40 de sobra): otra fuente de fragmentación interna.
 *
 * <h3>Orden de salida: FIFO por lote</h3>
 * Al retirar mercadería se consumen primero los lotes más antiguos. Las ubicaciones que quedan
 * vacías se liberan, y los lotes nuevos las reutilizan: así, con el uso, un lote nuevo queda
 * repartido en los huecos que dejaron los viejos.
 *
 * <h3>Sincronización</h3>
 * Un {@link ReentrantReadWriteLock}: las consultas (snapshots) comparten el lock de lectura; almacenar y
 * retirar, que son raros, toman el de escritura. Todo el estado se accede solo con el lock tomado.
 */
public final class Warehouse {

    private final WarehouseLayout layout;
    private final Map<String, Integer> unitVolumes = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // Estado protegido por `lock`.
    private final BitSet occupied;
    private final Slot[] slots;
    private final Map<String, Lot> lots = new LinkedHashMap<>(); // el orden de inserción es el de llegada
    private PlacementStrategy strategy;
    private long rejectedUnits;

    public Warehouse(WarehouseLayout layout, ProductCatalog catalog, PlacementStrategy strategy) {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        for (Product product : catalog.all()) {
            unitVolumes.put(product.id(), product.unitVolume());
        }
        this.occupied = new BitSet(layout.totalLocations());
        this.slots = new Slot[layout.totalLocations()];
    }

    /**
     * Almacena un lote, repartiéndolo en tantas ubicaciones libres como necesite. Si no hay
     * suficientes, almacena lo que cabe y devuelve el resto como rechazado.
     *
     * @throws IllegalArgumentException si el producto no existe, no cabe ni una unidad en una ubicación,
     *                                  o las unidades no son positivas
     * @throws IllegalStateException    si ya existe un lote con ese id
     */
    public StoreResult store(String lotId, String productId, int units) {
        Objects.requireNonNull(lotId, "lotId");
        if (units <= 0) {
            throw new IllegalArgumentException("units must be positive");
        }
        int unitsPerLocation = unitsPerLocation(productId);

        lock.writeLock().lock();
        try {
            if (lots.containsKey(lotId)) {
                throw new IllegalStateException("Lot " + lotId + " already exists");
            }
            int freeLocations = layout.totalLocations() - occupied.cardinality();
            int stored = (int) Math.min(units, (long) freeLocations * unitsPerLocation);
            int rejected = units - stored;
            rejectedUnits += rejected;

            List<Integer> assigned = stored == 0 ? List.of() : assign(lotId, productId, stored, unitsPerLocation);
            return new StoreResult(lotId, units, stored, rejected, assigned);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Retira unidades de un producto, de los lotes más antiguos primero, y libera las ubicaciones que
     * quedan vacías.
     *
     * @throws IllegalStateException    si hay menos unidades que las pedidas (el inventario y el almacén se desincronizaron)
     * @throws IllegalArgumentException si el producto no existe o las unidades no son positivas
     */
    public void retrieve(String productId, int units) {
        if (units <= 0) {
            throw new IllegalArgumentException("units must be positive");
        }
        unitsPerLocation(productId); // valida que el producto existe

        lock.writeLock().lock();
        try {
            if (unitsOf(productId) < units) {
                throw new IllegalStateException("Warehouse holds fewer than " + units + " units of " + productId);
            }
            int remaining = units;
            Iterator<Lot> lotIterator = lots.values().iterator();
            while (remaining > 0 && lotIterator.hasNext()) {
                Lot lot = lotIterator.next();
                if (lot.productId.equals(productId)) {
                    remaining -= takeFromLot(lot, remaining);
                    if (lot.locations.isEmpty()) {
                        lotIterator.remove();
                    }
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public PlacementStrategy strategy() {
        lock.readLock().lock();
        try {
            return strategy;
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Cambia la estrategia para los lotes que lleguen a partir de ahora; los ya almacenados no se mueven. */
    public void setStrategy(PlacementStrategy newStrategy) {
        Objects.requireNonNull(newStrategy, "newStrategy");
        lock.writeLock().lock();
        try {
            this.strategy = newStrategy;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Unidades que caben en una ubicación del producto. */
    public int unitsPerLocation(String productId) {
        Integer unitVolume = unitVolumes.get(productId);
        if (unitVolume == null) {
            throw new IllegalArgumentException("Unknown product: " + productId);
        }
        int units = layout.locationCapacity() / unitVolume;
        if (units < 1) {
            throw new IllegalArgumentException("Product " + productId + " does not fit in a location");
        }
        return units;
    }

    /** Foto consistente del almacén. */
    public WarehouseSnapshot snapshot() {
        lock.readLock().lock();
        try {
            List<LocationContent> contents = new ArrayList<>(slots.length);
            long wasted = 0;
            for (Slot slot : slots) {
                if (slot == null) {
                    contents.add(null);
                } else {
                    int usedVolume = slot.units * unitVolumes.get(slot.productId);
                    wasted += layout.locationCapacity() - usedVolume;
                    contents.add(new LocationContent(slot.lotId, slot.productId, slot.units, usedVolume));
                }
            }
            List<LotSnapshot> lotSnapshots = lots.values().stream().map(this::toSnapshot).toList();
            return new WarehouseSnapshot(layout, strategy, occupied.cardinality(), layout.totalLocations(),
                    wasted, rejectedUnits, contents, lotSnapshots);
        } finally {
            lock.readLock().unlock();
        }
    }

    // ---- Internos: se invocan con el lock correspondiente tomado ----

    /** Elige las ubicaciones, las marca ocupadas en el bitmap y las llena; devuelve sus índices. */
    private List<Integer> assign(String lotId, String productId, int units, int unitsPerLocation) {
        int needed = (units + unitsPerLocation - 1) / unitsPerLocation;
        List<Integer> chosen = strategy.pick(layout, occupied, needed);
        if (chosen.size() != needed) {
            throw new IllegalStateException("Strategy returned " + chosen.size() + " locations but " + needed + " were needed");
        }
        Lot lot = new Lot(lotId, productId);
        int remaining = units;
        for (int index : chosen) {
            int inThisLocation = Math.min(unitsPerLocation, remaining);
            slots[index] = new Slot(lotId, productId, inThisLocation);
            occupied.set(index);
            lot.locations.add(index);
            remaining -= inThisLocation;
        }
        lots.put(lotId, lot);
        return chosen;
    }

    /** Retira hasta {@code wanted} unidades de un lote, liberando las ubicaciones que se vacían; devuelve cuántas retiró. */
    private int takeFromLot(Lot lot, int wanted) {
        int taken = 0;
        Iterator<Integer> locationIterator = lot.locations.iterator();
        while (taken < wanted && locationIterator.hasNext()) {
            int index = locationIterator.next();
            Slot slot = slots[index];
            int fromHere = Math.min(slot.units, wanted - taken);
            slot.units -= fromHere;
            taken += fromHere;
            if (slot.units == 0) {
                slots[index] = null;
                occupied.clear(index);
                locationIterator.remove();
            }
        }
        return taken;
    }

    private int unitsOf(String productId) {
        int total = 0;
        for (Slot slot : slots) {
            if (slot != null && slot.productId.equals(productId)) {
                total += slot.units;
            }
        }
        return total;
    }

    private LotSnapshot toSnapshot(Lot lot) {
        List<Integer> sorted = lot.locations.stream().sorted().toList();
        int units = sorted.stream().mapToInt(index -> slots[index].units).sum();
        return new LotSnapshot(lot.lotId, lot.productId, units, sorted, countRuns(sorted));
    }

    /** Cantidad de tramos de índices consecutivos; 1 si el lote está todo junto. */
    private static int countRuns(List<Integer> sortedIndexes) {
        int runs = 0;
        for (int i = 0; i < sortedIndexes.size(); i++) {
            if (i == 0 || sortedIndexes.get(i).intValue() != sortedIndexes.get(i - 1) + 1) {
                runs++;
            }
        }
        return runs;
    }

    /** Contenido de una ubicación ocupada; solo se toca con el lock de {@link Warehouse} tomado. */
    private static final class Slot {
        private final String lotId;
        private final String productId;
        private int units;

        private Slot(String lotId, String productId, int units) {
            this.lotId = lotId;
            this.productId = productId;
            this.units = units;
        }
    }

    /** Ubicaciones de un lote, en el orden en que se asignaron. */
    private static final class Lot {
        private final String lotId;
        private final String productId;
        private final List<Integer> locations = new ArrayList<>();

        private Lot(String lotId, String productId) {
            this.lotId = lotId;
            this.productId = productId;
        }
    }
}
