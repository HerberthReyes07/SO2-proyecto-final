package com.redxela;

/** Punto de entrada: arranca la aplicación completa. */
public final class RedxelaApplication {

    private static final int DEFAULT_PORT = 7070;

    private RedxelaApplication() {
    }

    public static void main(String[] args) {
        ApplicationComponents application = new ApplicationComponents();

        // Al cerrar la JVM (Ctrl+C) se detienen ordenadamente los hilos propios y el servidor.
        Runtime.getRuntime().addShutdownHook(new Thread(application::close, "shutdown"));

        application.start(Integer.getInteger("redxela.port", DEFAULT_PORT));
    }
}
