package com.redxela.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redxela.sim.SimClock;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Prueba las rutas del reloj contra un servidor real en un puerto libre. */
class ClockRoutesTest {

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private ApiServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() {
        SimClock clock = new SimClock();
        SnapshotBroadcaster unusedBroadcaster = new SnapshotBroadcaster(() -> null);
        server = new ApiServer(unusedBroadcaster, List.of(new ClockRoutes(clock)));
        baseUrl = "http://localhost:" + server.start(0);
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void get_returns_the_current_clock_state() throws Exception {
        HttpResponse<String> response = send("GET", null);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json.readTree(response.body());
        assertThat(body.get("speed").asDouble()).isEqualTo(SimClock.DEFAULT_SPEED);
        assertThat(body.get("paused").asBoolean()).isFalse();
    }

    @Test
    void patch_changes_the_speed() throws Exception {
        HttpResponse<String> response = send("PATCH", "{\"speed\": 300}");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()).get("speed").asDouble()).isEqualTo(300.0);
    }

    @Test
    void patch_pauses_and_resumes() throws Exception {
        JsonNode paused = json.readTree(send("PATCH", "{\"paused\": true}").body());
        JsonNode resumed = json.readTree(send("PATCH", "{\"paused\": false}").body());

        assertThat(paused.get("paused").asBoolean()).isTrue();
        assertThat(resumed.get("paused").asBoolean()).isFalse();
    }

    @Test
    void patch_with_only_one_field_leaves_the_other_untouched() throws Exception {
        send("PATCH", "{\"speed\": 10, \"paused\": true}");

        JsonNode afterSpeedOnly = json.readTree(send("PATCH", "{\"speed\": 20}").body());

        assertThat(afterSpeedOnly.get("speed").asDouble()).isEqualTo(20.0);
        assertThat(afterSpeedOnly.get("paused").asBoolean()).isTrue();
    }

    @Test
    void invalid_speed_is_rejected_with_400_and_changes_nothing() throws Exception {
        HttpResponse<String> response = send("PATCH", "{\"speed\": 0, \"paused\": true}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json.readTree(response.body()).get("error").asText()).contains("speed");
        JsonNode state = json.readTree(send("GET", null).body());
        assertThat(state.get("paused").asBoolean()).isFalse();
        assertThat(state.get("speed").asDouble()).isEqualTo(SimClock.DEFAULT_SPEED);
    }

    @Test
    void malformed_body_is_rejected_with_400() throws Exception {
        assertThat(send("PATCH", "not json").statusCode()).isEqualTo(400);
    }

    private HttpResponse<String> send(String method, String body) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/clock"))
                .header("Content-Type", "application/json");
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
