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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Prueba el almacén de punta a punta: existencias iniciales, recepción, despacho y estrategia de ubicación. */
@Timeout(value = 40, unit = TimeUnit.SECONDS)
class WarehouseApiTest {

    private static final String STRATEGY_PATH = "/api/warehouse/strategy";

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
    void the_initial_stock_is_physically_stored_one_lot_per_product() throws Exception {
        Await.until("first snapshot", () -> snapshotMatches(snapshot -> true));

        JsonNode warehouse = latest().get("warehouse");
        assertThat(warehouse.get("layout").get("aisles").asInt()).isEqualTo(6);
        assertThat(warehouse.get("layout").get("positions").asInt()).isEqualTo(8);
        assertThat(warehouse.get("layout").get("levels").asInt()).isEqualTo(5);
        assertThat(warehouse.get("totalLocations").asInt()).isEqualTo(240);
        assertThat(warehouse.get("usedLocations").asInt()).isEqualTo(112); // 300 unidades de cada uno de los 15 productos
        assertThat(warehouse.get("locations")).hasSize(240);
        assertThat(warehouse.get("lots")).hasSize(15);
        assertThat(warehouse.get("lots").get(0).get("lotId").asText()).isEqualTo("INIT-P-001");
        assertThat(warehouse.get("rejectedUnits").asInt()).isZero();
        assertThat(warehouse.get("strategy").asText()).isEqualTo("FIRST_FREE");
    }

    @Test
    void the_catalog_describes_the_placement_strategies() throws Exception {
        JsonNode strategies = client.get("/api/catalog").body().get("placementStrategies");

        assertThat(strategies).hasSize(2);
        assertThat(strategies.get(0).get("id").asText()).isEqualTo("FIRST_FREE");
        for (JsonNode strategy : strategies) {
            assertThat(strategy.get("displayName").asText()).isNotBlank();
            assertThat(strategy.get("description").asText()).isNotBlank();
        }
    }

    @Test
    void changes_the_placement_strategy() throws Exception {
        assertThat(client.get(STRATEGY_PATH).body().get("strategy").asText()).isEqualTo("FIRST_FREE");

        Response change = client.put(STRATEGY_PATH, "{\"strategy\":\"LEAST_USED_AISLE\"}");

        assertThat(change.status()).isEqualTo(200);
        assertThat(change.body().get("strategy").asText()).isEqualTo("LEAST_USED_AISLE");
        Await.until("snapshot shows the new strategy", () -> snapshotMatches(snapshot ->
                snapshot.get("warehouse").get("strategy").asText().equals("LEAST_USED_AISLE")));
    }

    @Test
    void rejects_an_unknown_or_missing_strategy_with_400_and_keeps_the_current_one() throws Exception {
        assertThat(client.put(STRATEGY_PATH, "{\"strategy\":\"BOGUS\"}").status()).isEqualTo(400);
        assertThat(client.put(STRATEGY_PATH, "{}").status()).isEqualTo(400);
        assertThat(client.put(STRATEGY_PATH, "no es json").status()).isEqualTo(400);

        assertThat(client.get(STRATEGY_PATH).body().get("strategy").asText()).isEqualTo("FIRST_FREE");
    }

    @Test
    void a_delivery_becomes_a_new_lot_and_a_dispatched_order_takes_units_out_of_the_oldest_lots() throws Exception {
        client.post("/api/receptions", "{\"productId\":\"P-001\",\"quantity\":120}");
        Await.until("the delivery is stored as a lot", () -> snapshotMatches(snapshot -> lotsOf(snapshot, "LOT-TRK-0001") != null));

        JsonNode newLot = lotsOf(latest(), "LOT-TRK-0001");
        assertThat(newLot.get("productId").asText()).isEqualTo("P-001");
        assertThat(newLot.get("units").asInt()).isEqualTo(120);
        assertThat(newLot.get("locations")).hasSize(1);

        // Un pedido de 350 camisetas (3 × 100 + 50): sale del lote inicial (300) y luego del nuevo.
        placeOrder("CUS-003", "P-001", 100);
        placeOrder("CUS-003", "P-001", 100);
        placeOrder("CUS-003", "P-001", 100);
        placeOrder("CUS-003", "P-001", 50);
        Await.until("all four orders dispatched", () -> snapshotMatches(snapshot ->
                snapshot.get("stats").get("totals").get("completed").asInt() == 4));

        Await.until("initial lot exhausted and removed", () -> snapshotMatches(snapshot -> lotsOf(snapshot, "INIT-P-001") == null));
        assertThat(lotsOf(latest(), "LOT-TRK-0001").get("units").asInt()).isEqualTo(70); // 300 + 120 - 350
    }

    @Test
    void what_the_inventory_says_always_matches_what_is_physically_in_the_warehouse() throws Exception {
        client.post("/api/receptions", "{\"productId\":\"P-013\",\"quantity\":100,\"trucks\":5}");
        for (int i = 0; i < 6; i++) {
            placeOrder("CUS-00" + (1 + i % 5), i % 2 == 0 ? "P-013" : "P-007", 40);
        }

        Await.until("everything settled", () -> snapshotMatches(snapshot ->
                snapshot.get("stats").get("totals").get("completed").asInt() == 6
                        && snapshot.get("reception").get("trucks").isEmpty()
                        && snapshot.get("inventory").get("buffer").get("size").asInt() == 0
                        && snapshot.get("inventory").get("worker").get("processing").isNull()
                        && snapshot.get("scheduling").get("waitingTotal").asInt() == 0));

        JsonNode snapshot = latest();
        Map<String, Integer> physical = new HashMap<>();
        for (JsonNode lot : snapshot.get("warehouse").get("lots")) {
            physical.merge(lot.get("productId").asText(), lot.get("units").asInt(), Integer::sum);
        }
        for (JsonNode stock : snapshot.get("inventory").get("stock")) {
            int accounted = stock.get("available").asInt() + stock.get("reserved").asInt();
            assertThat(physical.getOrDefault(stock.get("productId").asText(), 0))
                    .as("physical units of %s", stock.get("productId").asText())
                    .isEqualTo(accounted);
        }
        assertThat(physical.get("P-013")).isEqualTo(300 + 500 - 3 * 40); // llegaron 500 y se despacharon 120
    }

    // ---- Utilidades ----

    private void placeOrder(String customerId, String productId, int quantity) throws Exception {
        Response response = client.post("/api/orders", "{\"customerId\":\"" + customerId + "\",\"lines\":[{\"productId\":\""
                + productId + "\",\"quantity\":" + quantity + "}]}");
        assertThat(response.status()).isEqualTo(201);
    }

    /** El lote con ese id en el snapshot, o null si no existe. */
    private static JsonNode lotsOf(JsonNode snapshot, String lotId) {
        for (JsonNode lot : snapshot.get("warehouse").get("lots")) {
            if (lot.get("lotId").asText().equals(lotId)) {
                return lot;
            }
        }
        return null;
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
