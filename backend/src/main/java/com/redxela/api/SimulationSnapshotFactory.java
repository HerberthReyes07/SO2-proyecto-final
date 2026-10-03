package com.redxela.api;

import com.redxela.generator.OrderGenerator;
import com.redxela.inventory.Inventory;
import com.redxela.inventory.InventorySnapshot;
import com.redxela.inventory.InventoryWorker;
import com.redxela.inventory.ReceptionBuffer;
import com.redxela.processing.OrderProcessor;
import com.redxela.processing.OrderScheduler;
import com.redxela.reception.ReceptionService;
import com.redxela.resources.ResourceRegistry;
import com.redxela.sim.ClockSnapshot;
import com.redxela.sim.SimClock;
import com.redxela.stats.SimulationStatistics;
import java.time.Duration;

/** Arma el {@link SimulationSnapshot} leyendo cada componente del núcleo. */
public final class SimulationSnapshotFactory {

    private final SimClock clock;
    private final ResourceRegistry resources;
    private final OrderScheduler scheduler;
    private final OrderProcessor processor;
    private final OrderGenerator generator;
    private final SimulationStatistics statistics;
    private final Inventory inventory;
    private final ReceptionBuffer receptionBuffer;
    private final InventoryWorker inventoryWorker;
    private final ReceptionService reception;

    public SimulationSnapshotFactory(SimClock clock, ResourceRegistry resources, OrderScheduler scheduler,
                                     OrderProcessor processor, OrderGenerator generator,
                                     SimulationStatistics statistics, Inventory inventory,
                                     ReceptionBuffer receptionBuffer, InventoryWorker inventoryWorker,
                                     ReceptionService reception) {
        this.clock = clock;
        this.resources = resources;
        this.scheduler = scheduler;
        this.processor = processor;
        this.generator = generator;
        this.statistics = statistics;
        this.inventory = inventory;
        this.receptionBuffer = receptionBuffer;
        this.inventoryWorker = inventoryWorker;
        this.reception = reception;
    }

    public SimulationSnapshot create() {
        // Se toma un solo instante del reloj y se usa para todo, así el avance de los pedidos
        // es coherente con la hora simulada que se muestra.
        ClockSnapshot clockSnapshot = clock.snapshot();
        Duration now = Duration.ofMillis(clockSnapshot.simulatedMillis());
        return new SimulationSnapshot(
                clockSnapshot,
                scheduler.snapshot(now),
                generator.snapshot(),
                statistics.snapshot(now),
                new InventorySnapshot(inventory.snapshot(), receptionBuffer.snapshot(), inventoryWorker.snapshot()),
                reception.snapshot(now),
                resources.snapshots(),
                processor.activeOrders(now),
                processor.completedCount());
    }
}
