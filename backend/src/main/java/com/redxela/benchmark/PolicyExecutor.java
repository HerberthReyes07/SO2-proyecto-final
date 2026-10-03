package com.redxela.benchmark;

import com.redxela.scheduling.SchedulingPolicy;
import java.util.List;
import java.util.function.IntConsumer;

/** Corre una política con una traza de carga y devuelve su resultado. Se separa del orquestador para poder probarlo sin simular nada. */
@FunctionalInterface
public interface PolicyExecutor {

    /**
     * @param onProgress se invoca con la cantidad de pedidos despachados a medida que avanza la corrida
     * @throws InterruptedException si la comparación se cancela mientras corre
     */
    PolicyResult execute(SchedulingPolicy policy, List<TraceEntry> trace, IntConsumer onProgress) throws InterruptedException;
}
