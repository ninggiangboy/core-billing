package dev.ngb.core_billing.loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.ngb.core_billing.Application;
import dev.ngb.core_billing.boot.jobs.OutboxJobs;
import dev.ngb.core_billing.boot.jobs.WebhookJobs;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.sharedkernel.event.EventPublisher;
import dev.ngb.core_billing.sharedkernel.id.TenantId;
import dev.ngb.core_billing.sharedkernel.runtime.UnitOfWork;
import dev.ngb.core_billing.support.PostgresContainerConfig;
import dev.ngb.core_billing.support.ScopeTestSupport;
import dev.ngb.core_billing.support.TestEvent;
import dev.ngb.core_billing.webhook.application.endpoint.command.CreateEndpointUseCase;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The 5s target, measured: 500 tenants, 3 receivers that sleep past the delivery deadline, and the real worker
 * jobs. Tagged {@code loadtest} and excluded from {@code test}; run it with {@code ops/loadtest/run.sh}.
 *
 * <p>It fails when the healthy tenants' p95 reaches 5s or any request ever waits for a database connection. Run it
 * with {@code billing.jobs.tenant-parallelism=1} to see the head-of-line delay this plan removes.
 */
@Tag("loadtest")
@SpringBootTest(classes = Application.class)
@ActiveProfiles("worker")
@Timeout(300)
class WebhookLagLoadTest {

    private static final int TENANTS = 500;
    private static final int SLOW_RECEIVERS = 3;
    private static final Audit AUDIT = new Audit("loadtest", "loadtest");

    @DynamicPropertySource
    static void wiring(DynamicPropertyRegistry registry) {
        // Deliberately not importing PostgresContainerConfig: its registrar pins the pool to 4 connections.
        PostgreSQLContainer postgres = PostgresContainerConfig.postgres();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", () -> PostgresContainerConfig.APP_ROLE);
        registry.add("spring.datasource.password", () -> PostgresContainerConfig.APP_PASSWORD);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "20");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "5");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.flyway.placeholders.appDbPassword", () -> PostgresContainerConfig.APP_PASSWORD);
        registry.add("billing.secrets.master-key", () -> PostgresContainerConfig.TEST_MASTER_KEY);
        registry.add("billing.jobs.tenant-parallelism", () -> "8");
        registry.add("billing.webhooks.allow-insecure-targets", () -> "true");
        registry.add("billing.webhooks.max-attempts", () -> "3");
        registry.add("billing.payments.jobs-enabled", () -> "false");
        // The test drives every tick itself; the one tick at startup sees only the platform tenant.
        registry.add("billing.outbox.poll-delay", () -> "PT1H");
        registry.add("billing.webhooks.poll-delay", () -> "PT1H");
        registry.add("billing.jobs.expire-reservations-delay", () -> "PT1H");
        registry.add("billing.jobs.verify-ledger-initial-delay", () -> "PT1H");
        registry.add("billing.invoicing.cycle-delay", () -> "PT1H");
        registry.add("billing.invoicing.renewal-delay", () -> "PT1H");
        registry.add("billing.rate-limit.cleanup-delay", () -> "PT1H");
    }

    @Autowired ScopeTestSupport scopes;
    @Autowired EventPublisher events;
    @Autowired UnitOfWork unitOfWork;
    @Autowired CreateEndpointUseCase createEndpoint;
    @Autowired OutboxJobs outboxJobs;
    @Autowired WebhookJobs webhookJobs;
    @Autowired MeterRegistry meters;
    @Autowired JsonMapper json;

    @Test
    void healthyTenantsMeetTheLagTargetWhileThreeReceiversAreStuck() throws Exception {
        JdbcTemplate owner = new JdbcTemplate(new DriverManagerDataSource(
                PostgresContainerConfig.postgres().getJdbcUrl(),
                PostgresContainerConfig.postgres().getUsername(),
                PostgresContainerConfig.postgres().getPassword()));
        List<TenantId> tenantIds = seedTenants(owner);

        try (RecordingReceiver fast = new RecordingReceiver(Duration.ZERO);
             RecordingReceiver slow = new RecordingReceiver(Duration.ofSeconds(15))) {
            List<TenantId> healthy = tenantIds.subList(SLOW_RECEIVERS, tenantIds.size());
            for (int i = 0; i < tenantIds.size(); i++) {
                String url = i < SLOW_RECEIVERS ? slow.url() : fast.url();
                TenantId tenant = tenantIds.get(i);
                scopes.inTenant(tenant, () -> createEndpoint.execute(
                        new CreateEndpointUseCase.Command(url, "loadtest", List.of("*"), AUDIT)));
            }

            AtomicLong maxPending = new AtomicLong();
            Thread sampler = startPendingSampler(maxPending);

            Instant started = Instant.now();
            for (TenantId tenant : tenantIds) {
                TestEvent event = TestEvent.forTenant(tenant);
                scopes.runInTenant(tenant, () -> unitOfWork.transactional(() -> events.publish(event)));
            }
            outboxJobs.relay();

            Instant deadline = Instant.now().plusSeconds(120);
            while (fast.received().size() < healthy.size() && Instant.now().isBefore(deadline)) {
                webhookJobs.deliver();
                Thread.sleep(100);
            }
            sampler.interrupt();

            List<Long> lags = new ArrayList<>();
            for (RecordingReceiver.Received received : fast.received()) {
                JsonNode body = json.readTree(received.body());
                Instant occurredAt = Instant.parse(body.get("occurredAt").asString());
                lags.add(Duration.between(occurredAt, received.at()).toMillis());
            }
            Collections.sort(lags);
            long p50 = percentile(lags, 50);
            long p95 = percentile(lags, 95);
            long p99 = percentile(lags, 99);
            long max = lags.isEmpty() ? 0 : lags.get(lags.size() - 1);
            long elapsedMs = Duration.between(started, Instant.now()).toMillis();

            System.out.println("=== webhook lag load test ===");
            System.out.println("tenants seeded: " + tenantIds.size());
            System.out.println("healthy deliveries: " + lags.size() + "/" + healthy.size());
            System.out.println("slow receiver attempts: " + slow.received().size() + "/" + SLOW_RECEIVERS);
            System.out.println("hikaricp.connections.pending max: " + maxPending.get());
            System.out.println("elapsed ms: " + elapsedMs);
            System.out.println("lag ms: p50=" + p50 + " p95=" + p95 + " p99=" + p99 + " max=" + max);

            assertThat(lags).as("every healthy tenant's webhook arrived").hasSize(healthy.size());
            assertThat(p95).as("healthy p95 stays below the 5s target").isLessThan(5_000);
            assertThat(maxPending.get()).as("no request ever waited for a database connection").isLessThanOrEqualTo(1);
            assertThat(slow.received()).as("the slow deliveries were attempted and left in flight").hasSizeGreaterThanOrEqualTo(SLOW_RECEIVERS);
        }
    }

    private static List<TenantId> seedTenants(JdbcTemplate owner) {
        List<Object[]> rows = new ArrayList<>();
        List<TenantId> ids = new ArrayList<>();
        for (int i = 0; i < TENANTS; i++) {
            TenantId id = new TenantId(UUID.randomUUID());
            ids.add(id);
            rows.add(new Object[] {id.value(), "loadtest-" + i, "Load test " + i});
        }
        owner.batchUpdate(
                "insert into tenants (id, code, name, status, created_at) values (?, ?, ?, 'ACTIVE', now())", rows);
        return ids;
    }

    private Thread startPendingSampler(AtomicLong maximum) {
        Thread sampler = Thread.ofPlatform().daemon().start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                long pending = meters.find("hikaricp.connections.pending").gauges().stream()
                        .mapToLong(WebhookLagLoadTest::value)
                        .max()
                        .orElse(0);
                maximum.accumulateAndGet(pending, Math::max);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        return sampler;
    }

    private static long value(Gauge gauge) {
        double value = gauge.value();
        return Double.isNaN(value) ? 0 : (long) value;
    }

    private static long percentile(List<Long> sorted, double percent) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(percent / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
    }

    /** Records when each body arrived; a slow one keeps the request open before answering. */
    private static final class RecordingReceiver implements AutoCloseable {

        private record Received(Instant at, String body) {}

        private final HttpServer server;
        private final List<Received> received = new CopyOnWriteArrayList<>();
        private final ExecutorService executor = Executors.newFixedThreadPool(32);

        RecordingReceiver(Duration delay) throws IOException {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/hook", exchange -> handle(exchange, delay));
            server.setExecutor(executor);
            server.start();
        }

        private void handle(HttpExchange exchange, Duration delay) throws IOException {
            try {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                received.add(new Received(Instant.now(), body));
                if (!delay.isZero()) {
                    Thread.sleep(delay.toMillis());
                }
                exchange.sendResponseHeaders(200, -1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException clientGone) {
                // the sender gave up on the slow receiver; that is the point of the test
            } finally {
                exchange.close();
            }
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
        }

        List<Received> received() {
            return List.copyOf(received);
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
