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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * El deadlock de punta a punta con el sistema real y el reloj a ×60 (la velocidad por defecto): se
 * provoca, se detecta, se resuelve de las dos maneras y se comprueba que con la prevención no ocurre.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class DeadlockApiTest {

    private static final String DEADLOCK_PATH = "/api/deadlock";

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
        socket = connect(port);
    }

    @AfterEach
    void stopApplication() {
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "test finished");
        application.close();
    }

    // ---- Prevención ----

    @Test
    void prevention_is_on_by_default_and_can_be_switched() throws Exception {
        assertThat(client.get(DEADLOCK_PATH).body().get("prevention").asText()).isEqualTo("GLOBAL_ORDER");

        Response change = client.put(DEADLOCK_PATH + "/prevention", "{\"mode\":\"NONE\"}");

        assertThat(change.status()).isEqualTo(200);
        assertThat(change.body().get("prevention").asText()).isEqualTo("NONE");
        assertThat(client.get(DEADLOCK_PATH).body().get("prevention").asText()).isEqualTo("NONE");
    }

    @Test
    void rejects_an_unknown_or_missing_prevention_mode_with_400() throws Exception {
        assertThat(client.put(DEADLOCK_PATH + "/prevention", "{\"mode\":\"BOGUS\"}").status()).isEqualTo(400);
        assertThat(client.put(DEADLOCK_PATH + "/prevention", "{}").status()).isEqualTo(400);

        assertThat(client.get(DEADLOCK_PATH).body().get("prevention").asText()).isEqualTo("GLOBAL_ORDER");
    }

    @Test
    void the_catalog_describes_the_prevention_modes() throws Exception {
        JsonNode modes = client.get("/api/catalog").body().get("preventionModes");

        assertThat(modes).hasSize(2);
        assertThat(modes.get(0).get("id").asText()).isEqualTo("NONE");
        assertThat(modes.get(1).get("id").asText()).isEqualTo("GLOBAL_ORDER");
        modes.forEach(mode -> assertThat(mode.get("description").asText()).isNotBlank());
    }

    @Test
    void the_snapshot_reports_no_deadlock_when_there_is_none() throws Exception {
        Await.until("first snapshot", () -> snapshotMatches(snapshot -> true));

        JsonNode deadlock = latest().get("deadlock");
        assertThat(deadlock.get("current").isNull()).isTrue();
        assertThat(deadlock.get("detected").asInt()).isZero();
        assertThat(deadlock.get("prevention").asText()).isEqualTo("GLOBAL_ORDER");
    }

    // ---- Resolver sin deadlock / entradas inválidas ----

    @Test
    void resolving_when_there_is_no_deadlock_is_a_409() throws Exception {
        Response response = client.post(DEADLOCK_PATH + "/resolve", "{\"continueOperationId\":\"ORD-0001\"}");

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.body().get("error").asText()).isNotBlank();
    }

    @Test
    void resolving_without_saying_who_continues_is_a_400() throws Exception {
        assertThat(client.post(DEADLOCK_PATH + "/resolve", "{}").status()).isEqualTo(400);
        assertThat(client.post(DEADLOCK_PATH + "/resolve", "no es json").status()).isEqualTo(400);
    }

    // ---- El escenario ----

    @Test
    void the_snapshot_says_no_scenario_has_run_yet() throws Exception {
        Await.until("first snapshot", () -> snapshotMatches(snapshot -> true));

        JsonNode scenario = latest().get("scenario");
        assertThat(scenario.get("phase").asText()).isEqualTo("IDLE");
        assertThat(scenario.get("mode").isNull()).isTrue();
    }

    @Test
    void the_scenario_with_prevention_reports_that_it_is_running_and_then_that_it_finished_without_deadlock() throws Exception {
        client.post(DEADLOCK_PATH + "/scenario", "{\"mode\":\"GLOBAL_ORDER\"}");

        Await.until("scenario running", () -> snapshotMatches(snapshot ->
                snapshot.get("scenario").get("phase").asText().equals("RUNNING")));
        assertThat(latest().get("scenario").get("mode").asText()).isEqualTo("GLOBAL_ORDER");

        Await.until("scenario finished", () -> snapshotMatches(snapshot ->
                snapshot.get("scenario").get("phase").asText().equals("FINISHED_CLEAN")));
        assertThat(latest().get("scenario").get("mode").asText()).isEqualTo("GLOBAL_ORDER");
    }

    @Test
    void the_scenario_without_prevention_stays_running_while_deadlocked_and_finishes_with_deadlock_once_resolved()
            throws Exception {
        provokeDeadlock();
        assertThat(latest().get("scenario").get("phase").asText()).isEqualTo("RUNNING"); // no termina mientras siga el deadlock

        client.post(DEADLOCK_PATH + "/resolve", "{\"continueOperationId\":\"ORD-0001\"}");

        Await.until("scenario finished having had a deadlock", () -> snapshotMatches(snapshot ->
                snapshot.get("scenario").get("phase").asText().equals("FINISHED_WITH_DEADLOCK")));
        assertThat(latest().get("scenario").get("mode").asText()).isEqualTo("NONE");
    }

    @Test
    void a_new_scenario_can_be_launched_once_the_previous_one_finished() throws Exception {
        client.post(DEADLOCK_PATH + "/scenario", "{\"mode\":\"GLOBAL_ORDER\"}");
        Await.until("first scenario finished", () -> snapshotMatches(snapshot ->
                snapshot.get("scenario").get("phase").asText().equals("FINISHED_CLEAN")));

        Response second = client.post(DEADLOCK_PATH + "/scenario", "{\"mode\":\"GLOBAL_ORDER\"}");

        assertThat(second.status()).isEqualTo(202);
        Await.until("second scenario finished", () -> snapshotMatches(snapshot ->
                snapshot.get("scenario").get("phase").asText().equals("FINISHED_CLEAN")
                        && snapshot.get("stats").get("totals").get("completed").asInt() == 2));
    }

    @Test
    void the_scenario_without_prevention_ends_in_a_deadlock_that_the_detector_explains() throws Exception {
        Response started = client.post(DEADLOCK_PATH + "/scenario", "{\"mode\":\"NONE\"}");
        assertThat(started.status()).isEqualTo(202);

        Await.until("deadlock confirmed", () -> snapshotMatches(snapshot -> !snapshot.get("deadlock").get("current").isNull()));

        JsonNode deadlock = latest().get("deadlock");
        JsonNode report = deadlock.get("current");
        assertThat(deadlock.get("prevention").asText()).isEqualTo("NONE");
        assertThat(deadlock.get("detected").asInt()).isEqualTo(1);
        assertThat(report.get("id").asText()).isEqualTo("DL-0001");
        assertThat(operationIds(report)).containsExactly("ORD-0001", "TRK-0001", "TRK-0002");
        assertThat(texts(report.get("resources"))).containsExactly("SCANNER", "LOADING_DOCK");
        JsonNode order = operation(report, "ORD-0001");
        assertThat(order.get("kind").asText()).isEqualTo("ORDER");
        assertThat(texts(order.get("held"))).contains("SCANNER");
        assertThat(order.get("waitingFor").asText()).isEqualTo("LOADING_DOCK");
        JsonNode truck = operation(report, "TRK-0001");
        assertThat(truck.get("kind").asText()).isEqualTo("TRUCK");
        assertThat(texts(truck.get("held"))).contains("LOADING_DOCK");
        assertThat(truck.get("waitingFor").asText()).isEqualTo("SCANNER");
        assertThat(report.get("conflict").get("waiter").asText()).isEqualTo("ORD-0001");
        assertThat(report.get("conflict").get("holder").asText()).isEqualTo("TRK-0001");
    }

    @Test
    void resolving_in_favour_of_the_order_returns_a_truck_to_waiting_and_everything_finishes() throws Exception {
        provokeDeadlock();

        Response resolution = client.post(DEADLOCK_PATH + "/resolve", "{\"continueOperationId\":\"ORD-0001\"}");

        assertThat(resolution.status()).isEqualTo(200);
        assertThat(resolution.body().get("continued").asText()).isEqualTo("ORD-0001");
        assertThat(resolution.body().get("returnedToWaiting").asText()).isEqualTo("TRK-0001");
        assertEverythingFinishes();
        assertThat(stockOf("P-010").get("available").asInt()).isEqualTo(299); // el pedido sacó 1 unidad, una sola vez
        assertThat(stockOf("P-001").get("available").asInt()).isEqualTo(310);  // y los dos camiones entregaron 5 + 5
    }

    @Test
    void resolving_in_favour_of_a_truck_returns_the_order_to_the_waiting_list_and_it_is_dispatched_later() throws Exception {
        provokeDeadlock();

        Response resolution = client.post(DEADLOCK_PATH + "/resolve", "{\"continueOperationId\":\"TRK-0001\"}");

        assertThat(resolution.status()).isEqualTo(200);
        assertThat(resolution.body().get("returnedToWaiting").asText()).isEqualTo("ORD-0001");
        assertEverythingFinishes();
        // El pedido devolvió su reserva al ser expulsado y la volvió a hacer al reintentarse: no se descontó dos veces.
        JsonNode meds = stockOf("P-010");
        assertThat(meds.get("available").asInt()).isEqualTo(299);
        assertThat(meds.get("reserved").asInt()).isZero();
        assertThat(stockOf("P-001").get("available").asInt()).isEqualTo(310);
    }

    @Test
    void resolving_in_favour_of_an_operation_outside_the_conflict_is_a_400_and_the_deadlock_remains() throws Exception {
        provokeDeadlock();

        Response response = client.post(DEADLOCK_PATH + "/resolve", "{\"continueOperationId\":\"TRK-0002\"}");

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body().get("error").asText()).contains("ORD-0001");
        assertThat(latest().get("deadlock").get("current").isNull()).isFalse();
    }

    @Test
    void the_same_scenario_with_global_ordering_never_deadlocks_and_completes_on_its_own() throws Exception {
        Response started = client.post(DEADLOCK_PATH + "/scenario", "{\"mode\":\"GLOBAL_ORDER\"}");
        assertThat(started.status()).isEqualTo(202);

        assertEverythingFinishes();

        for (String message : new ArrayList<>(messages)) {
            assertThat(parse(message).get("deadlock").get("current").isNull()).as("no snapshot ever showed a deadlock").isTrue();
        }
        assertThat(latest().get("deadlock").get("detected").asInt()).isZero();
        assertThat(latest().get("deadlock").get("prevention").asText()).isEqualTo("GLOBAL_ORDER");
    }

    // ---- El escenario exige un sistema en reposo ----

    @Test
    void a_second_scenario_is_refused_while_the_first_one_is_running() throws Exception {
        assertThat(client.post(DEADLOCK_PATH + "/scenario", "{\"mode\":\"GLOBAL_ORDER\"}").status()).isEqualTo(202);

        Response second = client.post(DEADLOCK_PATH + "/scenario", "{\"mode\":\"GLOBAL_ORDER\"}");

        assertThat(second.status()).isEqualTo(409);
        assertThat(second.body().get("error").asText()).isNotBlank();
        assertEverythingFinishes(); // y el primero sigue su curso
    }

    @Test
    void the_scenario_is_refused_while_the_generator_is_running() throws Exception {
        client.put("/api/generator", "{\"enabled\":true}");

        Response response = client.post(DEADLOCK_PATH + "/scenario", "{\"mode\":\"NONE\"}");

        assertThat(response.status()).isEqualTo(409);
        client.put("/api/generator", "{\"enabled\":false}");
    }

    // ---- Utilidades ----

    private void provokeDeadlock() throws Exception {
        assertThat(client.post(DEADLOCK_PATH + "/scenario", "{\"mode\":\"NONE\"}").status()).isEqualTo(202);
        Await.until("deadlock confirmed", () -> snapshotMatches(snapshot -> !snapshot.get("deadlock").get("current").isNull()));
    }

    /** El pedido se despacha, los dos camiones entregan, el deadlock desaparece y no queda ningún recurso retenido. */
    private void assertEverythingFinishes() throws Exception {
        Await.until("order dispatched and both deliveries processed", () -> snapshotMatches(snapshot ->
                snapshot.get("stats").get("totals").get("completed").asInt() == 1
                        && snapshot.get("inventory").get("worker").get("processed").asInt() == 2
                        && snapshot.get("reception").get("trucks").isEmpty()
                        && snapshot.get("activeOrders").isEmpty()
                        && snapshot.get("scheduling").get("waitingTotal").asInt() == 0
                        && snapshot.get("deadlock").get("current").isNull()));
        for (JsonNode pool : latest().get("resources")) {
            assertThat(pool.get("holders")).as("holders of %s", pool.get("type").asText()).isEmpty();
        }
    }

    private JsonNode stockOf(String productId) {
        for (JsonNode stock : latest().get("inventory").get("stock")) {
            if (stock.get("productId").asText().equals(productId)) {
                return stock;
            }
        }
        throw new AssertionError("No stock entry for " + productId);
    }

    private static JsonNode operation(JsonNode report, String id) {
        for (JsonNode operation : report.get("operations")) {
            if (operation.get("id").asText().equals(id)) {
                return operation;
            }
        }
        throw new AssertionError("Operation " + id + " is not in the report");
    }

    private static List<String> operationIds(JsonNode report) {
        return report.get("operations").findValuesAsText("id");
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
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
