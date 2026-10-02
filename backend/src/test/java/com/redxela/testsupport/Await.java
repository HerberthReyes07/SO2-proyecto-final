package com.redxela.testsupport;

import java.time.Duration;
import java.util.function.BooleanSupplier;

/** Espera activa con tope de tiempo: evita {@code Thread.sleep} arbitrarios en los tests de concurrencia. */
public final class Await {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
    private static final long POLL_MILLIS = 2;

    private Await() {
    }

    /** Espera hasta que {@code condition} sea verdadera; falla el test si no ocurre a tiempo. */
    public static void until(BooleanSupplier condition) throws InterruptedException {
        until("condition", condition);
    }

    public static void until(String description, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + DEFAULT_TIMEOUT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Not met within " + DEFAULT_TIMEOUT.toSeconds() + "s: " + description);
            }
            Thread.sleep(POLL_MILLIS);
        }
    }
}
