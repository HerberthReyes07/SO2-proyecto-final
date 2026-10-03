package com.redxela.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/** Cliente del WebSocket de estado para los tests: guarda los snapshots recibidos y permite esperar a que se cumpla una condición. */
public final class SnapshotStream implements AutoCloseable {

    private final ObjectMapper json = new ObjectMapper();
    private final List<String> messages = new CopyOnWriteArrayList<>();
    private final AtomicBoolean closedByServer = new AtomicBoolean();
    private final WebSocket socket;

    public SnapshotStream(int port) {
        StringBuilder partial = new StringBuilder();
        this.socket = HttpClient.newHttpClient().newWebSocketBuilder()
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

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        closedByServer.set(true);
                        return null;
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        closedByServer.set(true);
                    }
                }).join();
    }

    /** Espera a que el servidor cierre esta conexión (por ejemplo, porque se está reiniciando). */
    public void awaitClosedByServer() throws InterruptedException {
        Await.until("the server closes the WebSocket", closedByServer::get);
    }

    /** El último snapshot recibido; falla si todavía no llegó ninguno. */
    public JsonNode latest() {
        if (messages.isEmpty()) {
            throw new AssertionError("No snapshot received yet");
        }
        return parse(messages.get(messages.size() - 1));
    }

    /** Espera a que el último snapshot cumpla la condición. */
    public void awaitSnapshot(String description, Predicate<JsonNode> condition) throws InterruptedException {
        Await.until(description, () -> !messages.isEmpty() && condition.test(latest()));
    }

    private JsonNode parse(String message) {
        try {
            return json.readTree(message);
        } catch (Exception e) {
            throw new IllegalStateException("Snapshot is not valid JSON: " + message, e);
        }
    }

    @Override
    public void close() {
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "test finished").exceptionally(error -> null);
    }
}
