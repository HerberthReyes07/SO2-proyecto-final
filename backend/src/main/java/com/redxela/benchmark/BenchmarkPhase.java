package com.redxela.benchmark;

/** En qué punto está la comparación. */
public enum BenchmarkPhase {

    /** No se ha lanzado ninguna desde que arrancó la aplicación. */
    IDLE,
    RUNNING,
    /** Terminó con las cinco políticas. */
    DONE,
    /** La canceló el usuario; conserva los resultados de las políticas que alcanzaron a terminar. */
    CANCELLED,
    /** Una corrida falló; conserva los resultados de las políticas anteriores. */
    FAILED
}
