package com.redxela.demo;

/** En qué punto está el escenario de demostración del deadlock. */
public enum ScenarioPhase {

    /** No se ha lanzado ninguno desde que arrancó la aplicación. */
    IDLE,
    /** Está en marcha: ya registró el pedido y registrará o ya registró los camiones, y el trabajo no terminó. */
    RUNNING,
    /** Terminó y no se produjo ningún deadlock (lo esperado con prevención). */
    FINISHED_CLEAN,
    /** Terminó habiéndose producido un deadlock, que hubo que resolver. */
    FINISHED_WITH_DEADLOCK
}
