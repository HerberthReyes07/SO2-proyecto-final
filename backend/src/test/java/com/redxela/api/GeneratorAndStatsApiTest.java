package com.redxela.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redxela.ApplicationComponents;
import com.redxela.sim.SimClock;
import com.redxela.testsupport.Await;
import com.redxela.testsupport.HttpTestClient;
import com.redxela.testsupport.HttpTestClient.Response;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Prueba el generador automático y las estadísticas de punta a punta, con el reloj a máxima velocidad. */
@Timeout(value = 40, unit = TimeUnit.SECONDS)
class GeneratorAndStatsApiTest {

    private static final String GENERATOR_PATH = "/api/generator";

    private final ObjectMapper json = new ObjectMapper();
    private final List<String> messages = new CopyOnWriteArrayList<>();
    private ApplicationComponents application;
    private HttpTestClient client;
    private WebSocket socket;

    @BeforeEach
    void startApplication() {
        application = new ApplicationComponents();
        int port = application.start(0);
        client = new HttpTestClient(port);
        application.clock().setSpeed(SimClock.MAX_SPEED);
        socket = connect(port);
    }

    @AfterEach
    void stopApplication() {
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "test finished");
        application.close();
    }

    @Test
    void the_generator_starts_disabled_with_the_default_intensity() throws Exception {
        Response response = client.get(GENERATOR_PATH);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().get("enabled").asBoolean()).isFalse();
        assertThat(response.body().get("ordersPerMinute").asDouble()).isEqualTo(1.0);
    }

    @Test
    void configures_the_generator_partially() throws Exception {
        Response response = client.put(GENERATOR_PATH, "{\"ordersPerMinute\": 3.5, \"seed\": 99}");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().get("ordersPerMinute").asDouble()).isEqualTo(3.5);
        assertThat(response.body().get("seed").asLong()).isEqualTo(99);
        assertThat(response.body().get("enabled").asBoolean()).isFalse(); // no se tocó
    }

    @Test
    void rejects_invalid_settings_with_400_and_changes_nothing() throws Exception {
        assertThat(client.put(GENERATOR_PATH, "{\"ordersPerMinute\": 0}").status()).isEqualTo(400);
        assertThat(client.put(GENERATOR_PATH, "{\"ordersPerMinute\": 9999, \"enabled\": true}").status()).isEqualTo(400);
        assertThat(client.put(GENERATOR_PATH, "no es json").status()).isEqualTo(400);

        JsonNode state = client.get(GENERATOR_PATH).body();
        assertThat(state.get("enabled").asBoolean()).isFalse();
        assertThat(state.get("ordersPerMinute").asDouble()).isEqualTo(1.0);
    }

    @Test
    void generated_orders_flow_through_the_system_and_show_up_in_the_statistics() throws Exception {
        client.put(GENERATOR_PATH, "{\"enabled\": true, \"ordersPerMinute\": 10, \"seed\": 7}");

        Await.until("orders completed and counted", () -> snapshotMatches(snapshot ->
                snapshot.get("stats").get("totals").get("completed").asInt() >= 5));
        client.put(GENERATOR_PATH, "{\"enabled\": false}");

        JsonNode snapshot = latest();
        assertThat(snapshot.get("generator").get("generated").asInt()).isGreaterThanOrEqualTo(5);
        JsonNode levels = snapshot.get("stats").get("levels");
        assertThat(levels).hasSize(5);
        assertThat(levels.get(0).get("level").asText()).isEqualTo("EXPRESS");
        long completedByLevel = 0;
        for (JsonNode level : levels) {
            completedByLevel += level.get("completed").asLong();
        }
        assertThat(completedByLevel).isGreaterThanOrEqualTo(5);
        assertThat(snapshot.get("stats").get("totals").get("avgProcessingMillis").asLong()).isPositive();
    }

    @Test
    void every_resource_reports_its_utilization_in_the_snapshot() throws Exception {
        client.put(GENERATOR_PATH, "{\"enabled\": true, \"ordersPerMinute\": 10, \"seed\": 7}");

        Await.until("some resource has been used", () -> snapshotMatches(snapshot -> {
            for (JsonNode pool : snapshot.get("resources")) {
                if (pool.get("utilization").asDouble() > 0) {
                    return true;
                }
            }
            return false;
        }));

        for (JsonNode pool : latest().get("resources")) {
            assertThat(pool.get("utilization").asDouble()).isBetween(0.0, 1.0);
        }
    }

    @Test
    void resetting_the_statistics_starts_the_counters_from_zero() throws Exception {
        for (int i = 0; i < 3; i++) {
            client.post("/api/orders", "{\"customerId\":\"CUS-003\",\"lines\":[{\"productId\":\"P-001\",\"quantity\":2}]}");
        }
        Await.until("three orders completed", () -> snapshotMatches(snapshot ->
                snapshot.get("stats").get("totals").get("completed").asInt() == 3));

        application.clock().pause(); // congela el reloj para que nada nuevo se complete tras el reinicio
        HttpTestClient.Response reset = client.post("/api/stats/reset", "{}");

        assertThat(reset.status()).isEqualTo(204);
        Await.until("counters back to zero", () -> snapshotMatches(snapshot ->
                snapshot.get("stats").get("totals").get("completed").asInt() == 0));
        assertThat(latest().get("stats").get("levels").get(2).get("maxWaitMillis").asLong()).isZero();
    }

    // ---- Utilidades ----

    private JsonNode latest() {
        assertThat(messages).as("snapshots received so far").isNotEmpty();
        return parse(messages.get(messages.size() - 1));
    }

    private boolean snapshotMatches(Predicate<JsonNode> condition) {
        return !messages.isEmpty() && condition.test(parse(messages.get(messages.size() - 1)));
    }

    private JsonNode parse(String message) {
        try {
            return json.readTree(message);
        } catch (Exception e) {
            throw new IllegalStateException("Snapshot is not valid JSON: " + message, e);
        }
    }

    private WebSocket connect(int port) {
        StringBuilder partial = new StringBuilder();
        return HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/ws"), new WebSocket.Listener() {
                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        partial.append(data);
                        if (last) {
                            messages.add(partial.toString());
                            partial.setLength(0);
                        }
                        webSocket.request(1);
                        return null;
                    }
                }).join();
    }
}
