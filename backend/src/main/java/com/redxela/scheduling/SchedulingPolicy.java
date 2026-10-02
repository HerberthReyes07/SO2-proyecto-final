package com.redxela.scheduling;

import com.redxela.domain.QueuedOrder;
import java.time.Duration;
import java.util.Comparator;

/**
 * Políticas de atención: deciden cuál de los pedidos en espera se atiende primero.
 *
 * <h3>Todas ordenan con una clave que no depende del tiempo actual</h3>
 * Esto es deliberado. Una política que dependiera de "ahora" (como el envejecimiento escrito como
 * {@code nivel − espera/T}) cambiaría el orden de los elementos <i>mientras</i> se ordenan, y los
 * algoritmos de ordenamiento de Java pueden fallar con comparadores inestables. Se evita porque el
 * término "ahora" aparece igual en ambos lados de la comparación y se cancela:
 * <pre>nivelA − (ahora − llegadaA)/T  &lt;  nivelB − (ahora − llegadaB)/T
 *   ⇔  llegadaA + nivelA·T  &lt;  llegadaB + nivelB·T</pre>
 * Así el envejecimiento se expresa como una clave fija por pedido.
 *
 * <p>Ninguna política es apropiativa: un pedido que ya está siendo procesado nunca se interrumpe.
 */
public enum SchedulingPolicy {

    FIFO("FIFO",
            "Orden de llegada. Ignora el nivel de servicio; sirve como línea base de comparación.",
            Comparator.comparingLong(QueuedOrder::sequence)),

    STRICT_PRIORITY("Prioridad estricta",
            "Siempre gana el nivel de servicio más urgente; dentro de un nivel, el que llegó primero. "
                    + "Puede dejar sin atender a los niveles bajos (inanición).",
            Comparator.<QueuedOrder>comparingInt(queued -> queued.order().serviceLevel().level())
                    .thenComparingLong(QueuedOrder::sequence)),

    AGING_PRIORITY("Prioridad con envejecimiento",
            "Cada nivel de servicio equivale a " + SchedulingPolicy.AGING_STEP_MINUTES
                    + " minutos simulados de espera: un pedido de nivel bajo que espera lo suficiente "
                    + "termina superando a uno urgente recién llegado. Evita la inanición.",
            Comparator.comparing(SchedulingPolicy::agedKey)
                    .thenComparingLong(QueuedOrder::sequence)),

    EARLIEST_DEADLINE_FIRST("Vencimiento más próximo (EDF)",
            "Gana el pedido cuyo límite de espera (llegada + tiempo objetivo de su nivel) vence antes. "
                    + "Combina nivel de servicio y tiempo esperado de forma natural.",
            Comparator.comparing(QueuedOrder::deadline)
                    .thenComparingLong(QueuedOrder::sequence)),

    SHORTEST_JOB_FIRST("Menor cantidad de artículos primero (SJF)",
            "Gana el pedido con menos artículos: libera estaciones más rápido, pero puede postergar "
                    + "indefinidamente a los pedidos grandes.",
            Comparator.<QueuedOrder>comparingInt(queued -> queued.order().totalItems())
                    .thenComparingLong(QueuedOrder::sequence));

    /**
     * Cuánta espera simulada equivale a un nivel de servicio en el envejecimiento. Es una constante
     * numérica (y no un {@code Duration}) porque las constantes del enum la usan en su descripción
     * antes de que se inicialicen los campos estáticos normales; el compilador la sustituye por su valor.
     */
    static final long AGING_STEP_MINUTES = 10;

    private final String displayName;
    private final String description;
    private final Comparator<QueuedOrder> comparator;

    SchedulingPolicy(String displayName, String description, Comparator<QueuedOrder> comparator) {
        this.displayName = displayName;
        this.description = description;
        this.comparator = comparator;
    }

    public String displayName() {
        return displayName;
    }

    /** Explicación del criterio de orden, pensada para mostrarse al usuario. */
    public String description() {
        return description;
    }

    /** Orden en que se atienden los pedidos: el menor es el primero. */
    public Comparator<QueuedOrder> comparator() {
        return comparator;
    }

    /** Clave del envejecimiento: llegada + nivel × paso. Ver la nota de la clase. */
    private static Duration agedKey(QueuedOrder queued) {
        long levelsOfWait = queued.order().serviceLevel().level();
        return queued.arrivalTime().plus(Duration.ofMinutes(AGING_STEP_MINUTES * levelsOfWait));
    }
}
