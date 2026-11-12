package dev.ngb.billing.webhook.adapter.delivery.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import dev.ngb.billing.webhook.application.delivery.port.WebhookSender;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The sender against a real local server: what it refuses, what it never follows and how it bounds time and size. */
class HttpSenderTest {

    private HttpServer server;
    private final AtomicInteger redirectTargetHits = new AtomicInteger();
    private final AtomicInteger okHits = new AtomicInteger();
    private String received;

    private String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/ok", exchange -> {
            okHits.incrementAndGet();
            received = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8) + "|" + exchange.getRequestHeaders().getFirst("Billing-Key-Id");
            reply(exchange, 204, new byte[0]);
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", base() + "/target");
            reply(exchange, 302, new byte[0]);
        });
        server.createContext("/target", exchange -> {
            redirectTargetHits.incrementAndGet();
            reply(exchange, 200, "target".getBytes(StandardCharsets.UTF_8));
        });
        server.createContext("/fail", exchange -> reply(exchange, 500, "boom".getBytes(StandardCharsets.UTF_8)));
        server.createContext("/huge", exchange -> reply(exchange, 200, "x".repeat(5_000_000).getBytes(StandardCharsets.UTF_8)));
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(3_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            reply(exchange, 200, new byte[0]);
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private static void reply(com.sun.net.httpserver.HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            } catch (IOException clientClosed) {
                // the sender stops reading a huge body on purpose
            }
        }
        exchange.close();
    }

    private static HttpComponentsWebhookSender sender(boolean allowNonPublic, Duration readTimeout) {
        return new HttpComponentsWebhookSender(
                allowNonPublic, 100, 25, Duration.ofSeconds(2), Duration.ofSeconds(2), readTimeout, Duration.ofSeconds(5));
    }

    private static WebhookSender.Request post(String url) {
        return new WebhookSender.Request(url, Map.of("Billing-Key-Id", "whk_1", "Content-Type", "application/json"), "{\"a\":1}".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void postsTheBodyAndHeadersAndReportsTheStatus() throws Exception {
        try (HttpComponentsWebhookSender sender = sender(true, Duration.ofSeconds(2))) {
            WebhookSender.Result result = sender.send(post(base() + "/ok"));
            assertThat(result.statusCode()).isEqualTo(204);
            assertThat(result.error()).isNull();
            assertThat(received).isEqualTo("{\"a\":1}|whk_1");
        }
    }

    @Test
    void refusesALoopbackDestinationWithoutConnecting() throws Exception {
        try (HttpComponentsWebhookSender sender = sender(false, Duration.ofSeconds(2))) {
            WebhookSender.Result result = sender.send(post(base() + "/ok"));
            assertThat(result.statusCode()).isNull();
            assertThat(result.error()).isEqualTo("blocked_destination");
            assertThat(okHits).hasValue(0);
        }
    }

    @Test
    void refusesAHostnameThatResolvesToLoopback() throws Exception {
        try (HttpComponentsWebhookSender sender = sender(false, Duration.ofSeconds(2))) {
            WebhookSender.Result result = sender.send(post("http://localhost:" + server.getAddress().getPort() + "/ok"));
            assertThat(result.error()).isEqualTo("blocked_destination");
            assertThat(okHits).hasValue(0);
        }
    }

    @Test
    void doesNotFollowRedirects() throws Exception {
        try (HttpComponentsWebhookSender sender = sender(true, Duration.ofSeconds(2))) {
            WebhookSender.Result result = sender.send(post(base() + "/redirect"));
            assertThat(result.statusCode()).isEqualTo(302);
            assertThat(redirectTargetHits).hasValue(0);
        }
    }

    @Test
    void aFailingReceiverIsAResultNotAnException() throws Exception {
        try (HttpComponentsWebhookSender sender = sender(true, Duration.ofSeconds(2))) {
            WebhookSender.Result result = sender.send(post(base() + "/fail"));
            assertThat(result.statusCode()).isEqualTo(500);
            assertThat(result.responseExcerpt()).isEqualTo("boom");
        }
    }

    @Test
    void readsOnlyTheStartOfALargeResponse() throws Exception {
        try (HttpComponentsWebhookSender sender = sender(true, Duration.ofSeconds(5))) {
            WebhookSender.Result result = sender.send(post(base() + "/huge"));
            assertThat(result.statusCode()).isEqualTo(200);
            assertThat(result.responseExcerpt()).hasSize(1024);
        }
    }

    @Test
    void aSlowReceiverTimesOut() throws Exception {
        try (HttpComponentsWebhookSender sender = sender(true, Duration.ofMillis(400))) {
            WebhookSender.Result result = sender.send(post(base() + "/slow"));
            assertThat(result.statusCode()).isNull();
            assertThat(result.error()).isEqualTo("timeout");
        }
    }

    @Test
    void anUnreachablePortIsAConnectionFailure() throws Exception {
        int closedPort;
        try (java.net.ServerSocket probe = new java.net.ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = probe.getLocalPort();
        }
        try (HttpComponentsWebhookSender sender = sender(true, Duration.ofSeconds(2))) {
            WebhookSender.Result result = sender.send(post("http://127.0.0.1:" + closedPort + "/x"));
            assertThat(result.statusCode()).isNull();
            assertThat(result.error()).isEqualTo("connection_failed");
        }
    }

    @Test
    void aSaturatedPerRoutePoolFailsFastWithTimeout() throws Exception {
        try (HttpComponentsWebhookSender sender = new HttpComponentsWebhookSender(
                true, 20, 1, Duration.ofSeconds(2), Duration.ofMillis(200), Duration.ofSeconds(10), Duration.ofSeconds(15));
             java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Future<WebhookSender.Result> holding = pool.submit(() -> sender.send(post(base() + "/slow")));
            Thread.sleep(150); // the first send takes the only connection to this route

            long started = System.nanoTime();
            WebhookSender.Result queued = pool.submit(() -> sender.send(post(base() + "/slow"))).get(5, java.util.concurrent.TimeUnit.SECONDS);
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;

            assertThat(queued.statusCode()).isNull();
            assertThat(queued.error()).as("a pool wait is a timeout, not a read failure").isEqualTo("timeout");
            assertThat(elapsedMs).as("far below the 10s read timeout").isLessThan(2_000);
            assertThat(holding.get(10, java.util.concurrent.TimeUnit.SECONDS).statusCode()).isEqualTo(200);
        }
    }
}
