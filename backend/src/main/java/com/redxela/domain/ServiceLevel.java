package com.redxela.domain;

import java.time.Duration;

/**
 * Nivel de servicio contratado por el cliente. Determina <i>cuándo</i> debe atenderse un pedido,
 * mientras que {@link MerchandiseType} determina <i>qué recursos</i> necesita.
 *
 * <p>El tiempo objetivo es un tiempo de espera máximo en <b>tiempo simulado</b> y funciona como
 * deadline: {@code deadline = llegada + targetWait}.
 */
public enum ServiceLevel {

    EXPRESS(1, "Exprés", Duration.ofMinutes(1)),         // "casi instantáneo"
    PRIORITY(2, "Prioritario", Duration.ofMinutes(10)),
    STANDARD(3, "Estándar", Duration.ofMinutes(30)),
    SCHEDULED(4, "Programado", Duration.ofMinutes(60)),
    ECONOMY(5, "Económico", Duration.ofMinutes(120));

    private final int level;
    private final String displayName;
    private final Duration targetWait;

    ServiceLevel(int level, String displayName, Duration targetWait) {
        this.level = level;
        this.displayName = displayName;
        this.targetWait = targetWait;
    }

    /** Número de nivel (1 = más urgente). */
    public int level() {
        return level;
    }

    public String displayName() {
        return displayName;
    }

    public Duration targetWait() {
        return targetWait;
    }
}
