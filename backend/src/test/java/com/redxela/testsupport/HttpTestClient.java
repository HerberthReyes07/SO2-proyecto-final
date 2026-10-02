package com.redxela.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Cliente HTTP mínimo para probar la API contra un servidor real. */
public final class HttpTestClient {

    /** Respuesta ya interpretada: código HTTP y cuerpo JSON. */
    public record Response(int status, JsonNode body) {
    }

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private final String baseUrl;

    public HttpTestClient(int port) {
        this.baseUrl = "http://localhost:" + port;
    }

    public Response get(String path) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET());
    }

    public Response post(String path, String body) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    private Response send(HttpRequest.Builder request) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), json.readTree(response.body()));
    }
}
