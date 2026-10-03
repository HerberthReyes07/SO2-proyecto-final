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

/** Prueba Recepción e Inventario de punta a punta: llegada de mercadería, buffer, stock y pedidos que esperan. */
@Timeout(value = 40, unit = TimeUnit.SECONDS)
class ReceptionApiTest {

    private static final String PRODUCT = "P-001";
    private static final int INITIAL_STOCK = 300;

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
    void registers_an_arrival_and_returns_the_truck_ids() throws Exception {
        Response response = client.post("/api/receptions", "{\"productId\":\"P-001\",\"quantity\":50}");

        assertThat(response.status()).isEqualTo(202);
        assertThat(response.body().get("truckIds")).hasSize(1);
        assertThat(response.body().get("truckIds").get(0).asText()).isEqualTo("TRK-0001");
    }

    @Test
    void registers_several_identical_trucks_at_once() throws Exception {
        Response response = client.post("/api/receptions", "{\"productId\":\"P-001\",\"quantity\":10,\"trucks\":4}");

        assertThat(response.status()).isEqualTo(202);
        assertThat(response.body().get("truckIds")).hasSize(4);
    }

    @Test
    void rejects_invalid_arrivals_with_400() throws Exception {
        assertThat(client.post("/api/receptions", "{\"productId\":\"P-999\",\"quantity\":10}").status()).isEqualTo(400);
        assertThat(client.post("/api/receptions", "{\"productId\":\"P-001\",\"quantity\":0}").status()).isEqualTo(400);
        assertThat(client.post("/api/receptions", "{\"productId\":\"P-001\",\"quantity\":5000}").status()).isEqualTo(400);
        assertThat(client.post("/api/receptions", "{\"productId\":\"P-001\",\"quantity\":10,\"trucks\":0}").status()).isEqualTo(400);
        assertThat(client.post("/api/receptions", "{\"productId\":\"P-001\",\"quantity\":10,\"trucks\":999}").status()).isEqualTo(400);
        assertThat(client.post("/api/receptions", "no es json").status()).isEqualTo(400);
    }

    @Test
    void the_snapshot_describes_the_inventory_and_the_buffer() throws Exception {
        Await.until("first snapshot", () -> snapshotMatches(snapshot -> true));

        JsonNode inventory = latest().get("inventory");
        assertThat(inventory.get("stock")).hasSize(15);
        assertThat(inventory.get("stock").get(0).get("available").asInt()).isEqualTo(INITIAL_STOCK);
        assertThat(inventory.get("stock").get(0).get("reserved").asInt()).isZero();
        assertThat(inventory.get("buffer").get("capacity").asInt()).isEqualTo(10);
        assertThat(latest().get("reception").get("trucks")).isEmpty();
    }

    @Test
    void an_arrival_goes_through_reception_the_buffer_and_inventory_and_frees_the_orders_that_waited_for_it()
            throws Exception {
        // Tres pedidos de 100 camisetas agotan las 300 existencias; el cuarto se queda esperando stock.
        for (int i = 0; i < 4; i++) {
            placeOrder("CUS-003", PRODUCT, 100);
        }
        Await.until("fourth order waiting for stock", () -> snapshotMatches(snapshot -> {
            JsonNode waiting = snapshot.get("scheduling").get("waiting");
            return waiting.size() == 1 && waiting.get(0).get("missingStock").size() == 1;
        }));

        JsonNode missing = latest().get("scheduling").get("waiting").get(0).get("missingStock").get(0);
        assertThat(missing.get("productId").asText()).isEqualTo(PRODUCT);
        assertThat(missing.get("missingUnits").asInt()).isEqualTo(100);
        Await.until("first three orders dispatched", () -> snapshotMatches(snapshot ->
                snapshot.get("stats").get("totals").get("completed").asInt() == 3));
        assertThat(stockOf(PRODUCT).get("available").asInt()).isZero();

        Response arrival = client.post("/api/receptions", "{\"productId\":\"" + PRODUCT + "\",\"quantity\":100}");
        assertThat(arrival.status()).isEqualTo(202);

        // Recepción → buffer → Inventario → reevaluación → el cuarto pedido se admite y se despacha.
        Await.until("fourth order dispatched", () -> snapshotMatches(snapshot ->
                snapshot.get("stats").get("totals").get("completed").asInt() == 4
                        && snapshot.get("scheduling").get("waitingTotal").asInt() == 0));
        Await.until("reception drained", () -> snapshotMatches(snapshot ->
                snapshot.get("reception").get("trucks").isEmpty()
                        && snapshot.get("inventory").get("buffer").get("size").asInt() == 0));
        JsonNode stock = stockOf(PRODUCT);
        assertThat(stock.get("available").asInt()).isZero();   // 300 + 100 recibidas − 400 despachadas
        assertThat(stock.get("reserved").asInt()).isZero();
        JsonNode buffer = latest().get("inventory").get("buffer");
        assertThat(buffer.get("accepted").asInt()).isEqualTo(1);
        assertThat(buffer.get("delivered").asInt()).isEqualTo(1);
        assertThat(latest().get("inventory").get("worker").get("processed").asInt()).isEqualTo(1);
    }

    @Test
    void a_burst_of_trucks_ends_up_in_the_inventory_without_losing_any_unit() throws Exception {
        client.post("/api/receptions", "{\"productId\":\"" + PRODUCT + "\",\"quantity\":20,\"trucks\":30}");

        Await.until("every truck reported and the inventory processed everything", () -> snapshotMatches(snapshot ->
                snapshot.get("inventory").get("worker").get("processed").asInt() == 30));

        assertThat(stockOf(PRODUCT).get("available").asInt()).isEqualTo(INITIAL_STOCK + 30 * 20);
        assertThat(latest().get("reception").get("trucks")).isEmpty();
        assertThat(latest().get("inventory").get("buffer").get("accepted").asInt()).isEqualTo(30);
        assertThat(latest().get("inventory").get("buffer").get("delivered").asInt()).isEqualTo(30);
    }

    // ---- Utilidades ----

    private void placeOrder(String customerId, String productId, int quantity) throws Exception {
        Response response = client.post("/api/orders", "{\"customerId\":\"" + customerId + "\",\"lines\":[{\"productId\":\""
                + productId + "\",\"quantity\":" + quantity + "}]}");
        assertThat(response.status()).isEqualTo(201);
    }

    private JsonNode stockOf(String productId) {
        for (JsonNode stock : latest().get("inventory").get("stock")) {
            if (stock.get("productId").asText().equals(productId)) {
                return stock;
            }
        }
        throw new AssertionError("No stock entry for " + productId);
    }

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
