package com.redxela.benchmark;

import com.redxela.generator.WorkloadGenerator;
import java.time.Duration;

/**
 * Una llegada de la traza: qué pedido llega y en qué instante simulado.
 *
 * @param arrivalAt instante simulado, desde el inicio de la corrida, en que llega el pedido
 * @param spec      el pedido que llega
 */
public record TraceEntry(Duration arrivalAt, WorkloadGenerator.OrderSpec spec) {
}
