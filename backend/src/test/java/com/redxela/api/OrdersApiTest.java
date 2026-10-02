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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Prueba el cableado real: catálogo, clientes, pedidos y el snapshot por WebSocket. */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class OrdersApiTest {

    private final ObjectMapper json = new ObjectMapper();
    private ApplicationComponents application;
    private HttpTestClient client;
    private int port;

    @BeforeEach
    void startApplication() {
        application = new ApplicationComponents();
        port = application.start(0);
        client = new HttpTestClient(port);
        application.clock().pause(); // los pedidos creados quedan congelados y son fáciles de observar
    }

    @AfterEach
    void stopApplication() {
        application.close();
    }

    @Test
    void catalog_exposes_products_types_and_service_levels() throws Exception {
        Response response = client.get("/api/catalog");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().get("products")).hasSizeGreaterThanOrEqualTo(15);
        assertThat(response.body().get("merchandiseTypes")).hasSize(5);
        assertThat(response.body().get("serviceLevels")).hasSize(5);
        assertThat(response.body().get("policies")).hasSize(5);
        JsonNode express = response.body().get("serviceLevels").get(0);
        assertThat(express.get("id").asText()).isEqualTo("EXPRESS");
        assertThat(express.get("targetWaitMinutes").asLong()).isEqualTo(1);
    }

    @Test
    void lists_the_sample_customers() throws Exception {
        Response response = client.get("/api/customers");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).hasSize(5);
    }

    @Test
    void creates_a_customer_with_the_chosen_service_level() throws Exception {
        Response response = client.post("/api/customers", "{\"name\":\"Nueva Tienda\",\"serviceLevel\":\"PRIORITY\"}");

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.body().get("name").asText()).isEqualTo("Nueva Tienda");
        assertThat(response.body().get("level").asText()).isEqualTo("PRIORITY");
        assertThat(client.get("/api/customers").body()).hasSize(6);
    }

    @Test
    void rejects_invalid_customers_with_400() throws Exception {
        assertThat(client.post("/api/customers", "{\"name\":\"  \",\"serviceLevel\":\"PRIORITY\"}").status()).isEqualTo(400);
        assertThat(client.post("/api/customers", "{\"name\":\"X\",\"serviceLevel\":\"PLATINUM\"}").status()).isEqualTo(400);
        assertThat(client.post("/api/customers", "{\"name\":\"X\"}").status()).isEqualTo(400);
        assertThat(client.post("/api/customers", "no es json").status()).isEqualTo(400);
    }

    @Test
    void places_an_order_and_confirms_it() throws Exception {
        Response response = client.post("/api/orders",
                "{\"customerId\":\"CUS-001\",\"lines\":[{\"productId\":\"P-001\",\"quantity\":3},{\"productId\":\"P-004\",\"quantity\":2}]}");

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.body().get("orderId").asText()).isEqualTo("ORD-0001");
        assertThat(response.body().get("totalItems").asInt()).isEqualTo(5);
    }

    @Test
    void rejects_invalid_orders_with_400_and_a_readable_error() throws Exception {
        Response unknownCustomer = client.post("/api/orders",
                "{\"customerId\":\"CUS-999\",\"lines\":[{\"productId\":\"P-001\",\"quantity\":1}]}");
        Response unknownProduct = client.post("/api/orders",
                "{\"customerId\":\"CUS-001\",\"lines\":[{\"productId\":\"P-999\",\"quantity\":1}]}");
        Response noLines = client.post("/api/orders", "{\"customerId\":\"CUS-001\",\"lines\":[]}");

        assertThat(unknownCustomer.status()).isEqualTo(400);
        assertThat(unknownCustomer.body().get("error").asText()).contains("CUS-999");
        assertThat(unknownProduct.status()).isEqualTo(400);
        assertThat(noLines.status()).isEqualTo(400);
        assertThat(client.post("/api/orders", "no es json").status()).isEqualTo(400);
    }

    @Test
    void a_placed_order_shows_up_in_the_websocket_snapshot_holding_its_resources() throws Exception {
        client.post("/api/orders",
                "{\"customerId\":\"CUS-002\",\"lines\":[{\"productId\":\"P-007\",\"quantity\":2}]}");

        List<String> messages = new CopyOnWriteArrayList<>();
        WebSocket socket = connect(messages);
        try {
            Await.until("snapshot where the order holds the forklift", () -> messages.stream()
                    .map(this::parse)
                    .anyMatch(snapshot -> hasOrderHoldingForklift(snapshot, "ORD-0001")));

            JsonNode order = parse(messages.get(messages.size() - 1)).get("activeOrders").get(0);
            assertThat(order.get("customerName").asText()).isEqualTo("Boticas del Sur");
            assertThat(order.get("serviceLevel").asText()).isEqualTo("PRIORITY");
            assertThat(order.get("totalItems").asInt()).isEqualTo(2);
            assertThat(order.get("stage").asText()).isEqualTo("PICKING");
        } finally {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "test finished");
        }
    }

    // ---- Utilidades ----

    private boolean hasOrderHoldingForklift(JsonNode snapshot, String orderId) {
        for (JsonNode order : snapshot.get("activeOrders")) {
            if (order.get("orderId").asText().equals(orderId)) {
                for (JsonNode held : order.get("held")) {
                    if (held.asText().equals("FORKLIFT")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private JsonNode parse(String message) {
        try {
            return json.readTree(message);
        } catch (Exception e) {
            throw new IllegalStateException("Snapshot is not valid JSON: " + message, e);
        }
    }

    /** Abre un WebSocket y acumula cada mensaje de texto completo (los fragmentos se unen hasta {@code last}). */
    private WebSocket connect(List<String> messages) {
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
