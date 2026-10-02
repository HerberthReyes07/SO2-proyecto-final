package com.redxela.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redxela.ApplicationComponents;
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
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Prueba la política de atención y la lista de espera a través de la API y el WebSocket. */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class SchedulingApiTest {

    private static final String POLICY_PATH = "/api/scheduling/policy";

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
        application.clock().pause(); // los pedidos admitidos quedan congelados y la lista de espera estable
        socket = connect(port);
    }

    @AfterEach
    void stopApplication() {
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "test finished");
        application.close();
    }

    @Test
    void the_default_policy_is_strict_priority() throws Exception {
        Response response = client.get(POLICY_PATH);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().get("policy").asText()).isEqualTo("STRICT_PRIORITY");
    }

    @Test
    void changes_the_policy() throws Exception {
        Response change = client.put(POLICY_PATH, "{\"policy\":\"EARLIEST_DEADLINE_FIRST\"}");

        assertThat(change.status()).isEqualTo(200);
        assertThat(change.body().get("policy").asText()).isEqualTo("EARLIEST_DEADLINE_FIRST");
        assertThat(client.get(POLICY_PATH).body().get("policy").asText()).isEqualTo("EARLIEST_DEADLINE_FIRST");
    }

    @Test
    void rejects_an_unknown_or_missing_policy_with_400_and_keeps_the_current_one() throws Exception {
        assertThat(client.put(POLICY_PATH, "{\"policy\":\"BOGUS\"}").status()).isEqualTo(400);
        assertThat(client.put(POLICY_PATH, "{}").status()).isEqualTo(400);
        assertThat(client.put(POLICY_PATH, "no es json").status()).isEqualTo(400);

        assertThat(client.get(POLICY_PATH).body().get("policy").asText()).isEqualTo("STRICT_PRIORITY");
    }

    @Test
    void the_catalog_describes_every_policy() throws Exception {
        JsonNode policies = client.get("/api/catalog").body().get("policies");

        assertThat(policies).hasSize(5);
        assertThat(policies.get(0).get("id").asText()).isEqualTo("FIFO");
        for (JsonNode policy : policies) {
            assertThat(policy.get("displayName").asText()).isNotBlank();
            assertThat(policy.get("description").asText()).isNotBlank();
        }
    }

    @Test
    void the_snapshot_shows_the_waiting_list_in_service_order_and_follows_policy_changes() throws Exception {
        // Doce pedidos pequeños llenan a los 10 encargados; los dos siguientes quedan esperando.
        IntStream.range(0, 12).forEach(i -> placeOrder("CUS-003", "P-001", 1));
        Await.until("ten admitted and two waiting", () -> snapshotMatches(snapshot ->
                snapshot.get("activeOrders").size() == 10 && snapshot.get("scheduling").get("waitingTotal").asInt() == 2));
        placeOrder("CUS-001", "P-001", 30); // ORD-0013: Exprés pero grande
        placeOrder("CUS-005", "P-001", 1);  // ORD-0014: Económico pero pequeño

        Await.until("both new orders listed", () -> snapshotMatches(snapshot ->
                snapshot.get("scheduling").get("waitingTotal").asInt() == 4));
        assertThat(waitingIds()).containsExactly("ORD-0013", "ORD-0011", "ORD-0012", "ORD-0014");

        client.put(POLICY_PATH, "{\"policy\":\"SHORTEST_JOB_FIRST\"}");

        // Con SJF gana el que tiene menos artículos; los tres de 1 artículo desempatan por llegada
        // y el de 30 artículos (que con prioridad estricta iba primero) pasa al final.
        Await.until("list reordered by size", () -> snapshotMatches(snapshot -> waitingIdsOf(snapshot)
                .equals(List.of("ORD-0011", "ORD-0012", "ORD-0014", "ORD-0013"))));
        assertThat(latest().get("scheduling").get("policy").asText()).isEqualTo("SHORTEST_JOB_FIRST");

        JsonNode economy = latest().get("scheduling").get("waiting").get(2);
        assertThat(economy.get("customerName").asText()).isEqualTo("Econo Mart");
        assertThat(economy.get("targetWaitMillis").asLong()).isEqualTo(120L * 60 * 1000);
    }

    // ---- Utilidades ----

    private void placeOrder(String customerId, String productId, int quantity) {
        try {
            Response response = client.post("/api/orders", "{\"customerId\":\"" + customerId + "\",\"lines\":[{\"productId\":\""
                    + productId + "\",\"quantity\":" + quantity + "}]}");
            assertThat(response.status()).isEqualTo(201);
        } catch (Exception e) {
            throw new AssertionError("Could not place the order", e);
        }
    }

    /** Último snapshot recibido; falla si todavía no llegó ninguno. */
    private JsonNode latest() {
        assertThat(messages).as("snapshots received so far").isNotEmpty();
        return parse(messages.get(messages.size() - 1));
    }

    /** Indica si el último snapshot cumple la condición; es falso mientras no haya llegado ninguno. */
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

    private List<String> waitingIds() {
        return waitingIdsOf(latest());
    }

    private static List<String> waitingIdsOf(JsonNode snapshot) {
        return snapshot.get("scheduling").get("waiting").findValuesAsText("orderId");
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
