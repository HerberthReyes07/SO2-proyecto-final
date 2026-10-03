package com.redxela.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.redxela.benchmark.BenchmarkPhase;
import com.redxela.benchmark.BenchmarkRunner;
import com.redxela.benchmark.PolicyResult;
import com.redxela.scheduling.SchedulingPolicy;
import com.redxela.stats.StatsSnapshot;
import com.redxela.stats.StatsTotals;
import com.redxela.testsupport.Await;
import com.redxela.testsupport.HttpTestClient;
import com.redxela.testsupport.HttpTestClient.Response;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Las rutas del comparador contra un servidor real, con un ejecutor falso que se puede retener. */
class BenchmarkApiTest {

    private static final String PATH = "/api/benchmark";

    private final CountDownLatch release = new CountDownLatch(1);
    private BenchmarkRunner runner;
    private ApiServer server;
    private HttpTestClient client;

    @BeforeEach
    void startServer() {
        runner = new BenchmarkRunner((policy, trace, progress) -> {
            release.await();
            StatsSnapshot stats = new StatsSnapshot(new StatsTotals(trace.size(), 0, 0, 1.0, 1.0, 1), List.of());
            return new PolicyResult(policy, stats, 1_000, 0.5);
        });
        server = new ApiServer(new SnapshotBroadcaster(() -> null), List.of(new BenchmarkRoutes(runner)));
        client = new HttpTestClient(server.start(0));
    }

    @AfterEach
    void stopServer() {
        release.countDown();
        runner.close();
        server.close();
    }

    @Test
    void starts_idle() throws Exception {
        assertThat(client.get(PATH).body().get("phase").asText()).isEqualTo("IDLE");
    }

    @Test
    void post_starts_a_comparison_with_defaults_and_answers_202() throws Exception {
        Response response = client.post(PATH, "{}");

        assertThat(response.status()).isEqualTo(202);
        assertThat(response.body().get("phase").asText()).isEqualTo("RUNNING");
        assertThat(response.body().get("params").get("orders").asInt()).isEqualTo(BenchmarkRoutes.DEFAULT_ORDERS);
        assertThat(response.body().get("params").get("ordersPerMinute").asDouble()).isEqualTo(BenchmarkRoutes.DEFAULT_ORDERS_PER_MINUTE);
    }

    @Test
    void post_uses_the_given_parameters() throws Exception {
        Response response = client.post(PATH, "{\"orders\":30,\"ordersPerMinute\":2.5,\"seed\":99}");

        assertThat(response.status()).isEqualTo(202);
        assertThat(response.body().get("params").get("orders").asInt()).isEqualTo(30);
        assertThat(response.body().get("params").get("seed").asLong()).isEqualTo(99);
    }

    @Test
    void post_with_out_of_range_parameters_answers_400() throws Exception {
        assertThat(client.post(PATH, "{\"orders\":1}").status()).isEqualTo(400);
        assertThat(client.post(PATH, "{\"ordersPerMinute\":0}").status()).isEqualTo(400);
        assertThat(runner.snapshot().phase()).isEqualTo(BenchmarkPhase.IDLE);
    }

    @Test
    void a_second_post_while_running_answers_409() throws Exception {
        client.post(PATH, "{}");

        assertThat(client.post(PATH, "{}").status()).isEqualTo(409);
    }

    @Test
    void delete_cancels_a_running_comparison() throws Exception {
        client.post(PATH, "{}");

        assertThat(client.delete(PATH).status()).isEqualTo(204);
        Await.until(() -> runner.snapshot().phase() == BenchmarkPhase.CANCELLED);
    }

    @Test
    void delete_without_a_running_comparison_is_not_an_error() throws Exception {
        assertThat(client.delete(PATH).status()).isEqualTo(204);
    }

    @Test
    void get_reports_the_results_when_done() throws Exception {
        client.post(PATH, "{\"orders\":10}");
        release.countDown();
        Await.until(() -> runner.snapshot().phase() == BenchmarkPhase.DONE);

        var body = client.get(PATH).body();

        assertThat(body.get("results")).hasSize(SchedulingPolicy.values().length);
        assertThat(body.get("results").get(0).get("policy").asText()).isEqualTo("FIFO");
    }
}
