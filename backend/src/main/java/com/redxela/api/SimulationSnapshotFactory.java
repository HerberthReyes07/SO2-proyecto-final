package com.redxela.api;

import com.redxela.processing.OrderProcessor;
import com.redxela.processing.OrderScheduler;
import com.redxela.resources.ResourceRegistry;
import com.redxela.sim.ClockSnapshot;
import com.redxela.sim.SimClock;
import java.time.Duration;

/** Arma el {@link SimulationSnapshot} leyendo cada componente del núcleo. */
public final class SimulationSnapshotFactory {

    private final SimClock clock;
    private final ResourceRegistry resources;
    private final OrderScheduler scheduler;
    private final OrderProcessor processor;

    public SimulationSnapshotFactory(
            SimClock clock, ResourceRegistry resources, OrderScheduler scheduler, OrderProcessor processor) {
        this.clock = clock;
        this.resources = resources;
        this.scheduler = scheduler;
        this.processor = processor;
    }

    public SimulationSnapshot create() {
        // Se toma un solo instante del reloj y se usa para todo, así el avance de los pedidos
        // es coherente con la hora simulada que se muestra.
        ClockSnapshot clockSnapshot = clock.snapshot();
        Duration now = Duration.ofMillis(clockSnapshot.simulatedMillis());
        return new SimulationSnapshot(
                clockSnapshot,
                scheduler.snapshot(now),
                resources.snapshots(),
                processor.activeOrders(now),
                processor.completedCount());
    }
}
