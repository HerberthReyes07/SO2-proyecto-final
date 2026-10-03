package com.redxela.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.redxela.ApplicationComponents;
import com.redxela.sim.SimClock;
import com.redxela.testsupport.Await;
import com.redxela.testsupport.HttpTestClient;
import com.redxela.testsupport.HttpTestClient.Response;
import com.redxela.testsupport.SnapshotStream;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Reiniciar la simulación contra el sistema real: vuelve todo al estado inicial salvo las cuentas de cliente. */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class SimulationResetApiTest {

    private static final String RESET_PATH = "/api/simulation/reset";
    private static final String OUR_THREADS =
            "dispatcher|inventory-worker|deadlock-detector|snapshot-broadcaster|order-generator|deadlock-scenario|truck-\\d+|order-\\d+";
    private static final int INITIAL_UNITS = 300;

    private ApplicationComponents application;
    private HttpTestClient client;
    private int port;
    private SnapshotStream stream;

    @BeforeEach
    void startApplication() {
        application = new ApplicationComponents();
        port = application.start(0);
        client = new HttpTestClient(port);
        application.clock().setSpeed(SimClock.MAX_SPEED);
        stream = new SnapshotStream(port);
    }

    @AfterEach
    void stopApplication() {
        stream.close();
        application.close();
    }

    @Test
    void the_reset_returns_everything_to_the_initial_state_but_keeps_the_customers() throws Exception {
        client.post("/api/customers", "{\"name\":\"Cliente Nuevo\",\"serviceLevel\":\"EXPRESS\"}");
        client.put("/api/generator", "{\"enabled\":true,\"ordersPerMinute\":3}");
        stream.awaitSnapshot("some orders dispatched", snapshot -> snapshot.get("completedOrders").asInt() > 0);

        Response response = client.post(RESET_PATH, "{}");
        assertThat(response.status()).isEqualTo(202);
        assertThat(response.body().get("status").asText()).isEqualTo("resetting");
        stream = reconnect();

        JsonNode snapshot = stream.latest();
        assertThat(snapshot.get("generator").get("enabled").asBoolean()).isFalse();
        assertThat(snapshot.get("completedOrders").asInt()).isZero();
        assertThat(snapshot.get("stats").get("totals").get("completed").asInt()).isZero();
        assertThat(snapshot.get("scheduling").get("waitingTotal").asInt()).isZero();
        assertThat(snapshot.get("activeOrders")).isEmpty();
        for (JsonNode stock : snapshot.get("inventory").get("stock")) {
            assertThat(stock.get("available").asInt()).isEqualTo(INITIAL_UNITS);
        }
        assertThat(client.get("/api/clock").body().get("speed").asDouble()).isEqualTo(SimClock.DEFAULT_SPEED);
        assertThat(client.get("/api/customers").body()).anySatisfy(
                customer -> assertThat(customer.get("name").asText()).isEqualTo("Cliente Nuevo"));
    }

    @Test
    void the_reset_clears_a_standing_deadlock() throws Exception {
        // La coreografía del escenario está pensada para la velocidad por defecto: a la máxima no llega a confirmarse.
        application.clock().setSpeed(SimClock.DEFAULT_SPEED);
        client.post("/api/deadlock/scenario", "{\"mode\":\"NONE\"}");
        stream.awaitSnapshot("deadlock detected", snapshot -> !snapshot.get("deadlock").get("current").isNull());

        client.post(RESET_PATH, "{}");
        stream = reconnect();

        JsonNode snapshot = stream.latest();
        assertThat(snapshot.get("deadlock").get("current").isNull()).isTrue();
        assertThat(snapshot.get("scenario").get("phase").asText()).isEqualTo("IDLE");
        for (JsonNode pool : snapshot.get("resources")) {
            assertThat(pool.get("holders")).isEmpty();
            assertThat(pool.get("waiting")).isEmpty();
        }
    }

    @Test
    void the_new_simulation_works() throws Exception {
        client.post(RESET_PATH, "{}");
        stream = reconnect();
        application.clock().setSpeed(SimClock.MAX_SPEED);

        String customerId = client.get("/api/customers").body().get(0).get("id").asText();
        client.post("/api/orders", "{\"customerId\":\"" + customerId + "\",\"lines\":[{\"productId\":\"P-001\",\"quantity\":2}]}");

        stream.awaitSnapshot("the order is dispatched", snapshot -> snapshot.get("completedOrders").asInt() == 1);
    }

    @Test
    void repeated_resets_do_not_leave_threads_behind() throws Exception {
        client.put("/api/generator", "{\"enabled\":true,\"ordersPerMinute\":3}");
        stream.awaitSnapshot("some orders in flight", snapshot -> snapshot.get("completedOrders").asInt() > 0);
        client.put("/api/generator", "{\"enabled\":false}");
        long before = simulationThreads();

        for (int i = 0; i < 3; i++) {
            client.post(RESET_PATH, "{}");
            stream = reconnect();
        }

        // Cada reinicio crea y destruye el mismo conjunto de hilos: si el total creciera, alguno se estaría quedando.
        assertThat(simulationThreads()).isLessThanOrEqualTo(before);
    }

    /**
     * Espera a que el servidor viejo caiga (su WebSocket se cierra) y a que otro nuevo atienda en el mismo puerto, y
     * abre un WebSocket contra él. Sin la primera espera se leería el estado de la simulación que se está descartando.
     */
    private SnapshotStream reconnect() throws InterruptedException {
        stream.awaitClosedByServer();
        SnapshotStream[] connected = new SnapshotStream[1];
        Await.until("the server answers again on the same port", () -> {
            try {
                connected[0] = new SnapshotStream(port);
                return true;
            } catch (RuntimeException e) {
                return false; // todavía reiniciando: se reintenta
            }
        });
        connected[0].awaitSnapshot("first snapshot of the new simulation", snapshot -> true);
        return connected[0];
    }

    private static long simulationThreads() {
        Pattern ours = Pattern.compile(OUR_THREADS);
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.isAlive() && ours.matcher(thread.getName()).matches())
                .count();
    }
}
