package dev.ngb.core_billing.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import dev.ngb.core_billing.Application;
import dev.ngb.core_billing.boot.infrastructure.messaging.OutboxRelay;
import dev.ngb.core_billing.invoicing.application.subscription.command.CreateSubscriptionUseCase;
import dev.ngb.core_billing.money.MoneyTestWorld;
import dev.ngb.core_billing.money.MoneyTestWorld.World;
import dev.ngb.core_billing.money.api.charge.ChargeCommand;
import dev.ngb.core_billing.money.api.charge.ChargeWallet;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.pricing.PricingTestWorld;
import dev.ngb.core_billing.pricing.api.plan.BillingCycle;
import dev.ngb.core_billing.sharedkernel.money.Currency;
import dev.ngb.core_billing.sharedkernel.money.Money;
import dev.ngb.core_billing.support.OwnerDatabase;
import dev.ngb.core_billing.support.PostgresContainerConfig;
import dev.ngb.core_billing.support.ScopeTestSupport;
import dev.ngb.core_billing.webhook.application.endpoint.command.CreateEndpointUseCase;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Two api and two worker contexts on one PostgreSQL. One worker is stopped while a webhook send is in flight; the
 * rest must finish the renewal, the outbox and the delivery exactly once.
 */
@SpringBootTest(classes = Application.class)
@Import(PostgresContainerConfig.class)
class MultiInstanceRuntimeIntegrationTest {

    private static final Audit AUDIT = new Audit("user/test", "req-multi");
    private static final Currency USD = MoneyTestWorld.USD;

    @Autowired MoneyTestWorld worlds;
    @Autowired PricingTestWorld pricing;
    @Autowired ScopeTestSupport scopes;
    @Autowired OwnerDatabase owner;
    @Autowired ChargeWallet chargeWallet;
    @Autowired CreateSubscriptionUseCase createSubscription;
    @Autowired CreateEndpointUseCase createEndpoint;
    @Autowired OutboxRelay relay;

    @Test
    void aWorkerCanDieMidRunWhileTheOthersFinishTheWorkOnce() throws Exception {
        World world = worlds.postpaid(50_000_000);
        var plan = pricing.plan(world.tenant().id(), "multi", pricing.pro(), BillingCycle.MONTHLY, null, null);
        var subscription = scopes.inTenant(world.tenant().id(), () -> createSubscription.execute(
                new CreateSubscriptionUseCase.Command(world.wallet().id(), plan.id().value(), "sub-" + UUID.randomUUID(), AUDIT, null)))
                .subscription();
        owner.jdbc().update("update subscriptions set next_renewal_at = now() - interval '1 second' where id = ?", subscription.id().value());

        scopes.inTenant(world.tenant().id(), () -> chargeWallet.charge(new ChargeCommand(
                world.wallet().id(), null, Money.of(700, USD), null, "work", "multi-" + UUID.randomUUID())));

        try (SlowReceiver receiver = new SlowReceiver()) {
            scopes.inTenant(world.tenant().id(), () -> createEndpoint.execute(
                    new CreateEndpointUseCase.Command(receiver.url(), "multi-instance", List.of("subscription.renewed"), AUDIT)));

            // Queue the period-zero delivery and the invoice lines before any worker starts, then move the lines to
            // the previous period so the cycle job has something to close.
            scopes.runInTenant(world.tenant().id(), relay::relayDue);
            owner.jdbc().update(
                    "update invoice_items set cycle_start = cycle_start - interval '1 month', cycle_end = cycle_end - interval '1 month'"
                            + " where tenant_id = ? and invoice_id is null",
                    world.tenant().id().value());

            List<ConfigurableApplicationContext> contexts = new ArrayList<>();
            ConfigurableApplicationContext firstWorker = null;
            try {
                contexts.add(start("api", false));
                contexts.add(start("api", false));
                firstWorker = start("worker", true);
                contexts.add(firstWorker);
                contexts.add(start("worker", true));

                assertThat(receiver.awaitFirstRequest()).as("a worker is sending a webhook").isTrue();
                firstWorker.close();
                receiver.release();

                String tenant = world.tenant().id().value().toString();
                await(Duration.ofSeconds(60), () -> owner.count(
                        "select count(*) from charges where tenant_id = ? and reference like ?",
                        UUID.fromString(tenant), "subscription:" + subscription.id() + ":%") == 2);
                await(Duration.ofSeconds(60), () -> owner.count("select count(*) from invoices where tenant_id = ?",
                        UUID.fromString(tenant)) == 1);
                await(Duration.ofSeconds(60), () -> owner.count(
                        "select count(*) from outbox where tenant_id = ? and status <> 'DELIVERED'",
                        UUID.fromString(tenant)) == 0);
                await(Duration.ofSeconds(60), () -> owner.count(
                        "select count(*) from webhook_deliveries where tenant_id = ? and event_type = 'subscription.renewed' and status = 'SUCCEEDED'",
                        UUID.fromString(tenant)) == 2);

                assertThat(owner.count("select count(*) from charges where tenant_id = ? and reference like ?",
                        UUID.fromString(tenant), "subscription:" + subscription.id() + ":%"))
                        .as("the renewal was charged once").isEqualTo(2);
                assertThat(owner.count("select count(*) from invoices where tenant_id = ?", UUID.fromString(tenant)))
                        .as("the closed period was invoiced once").isEqualTo(1);
                assertThat(owner.count(
                        "select count(*) from webhook_deliveries where tenant_id = ? and event_type = 'subscription.renewed'",
                        UUID.fromString(tenant)))
                        .as("one delivery per renewal event").isEqualTo(2);
            } finally {
                for (ConfigurableApplicationContext context : contexts) {
                    if (context.isActive()) {
                        context.close();
                    }
                }
            }
        }
    }

    private ConfigurableApplicationContext start(String profile, boolean jobs) {
        PostgreSQLContainer postgres = PostgresContainerConfig.postgres();
        List<String> args = new ArrayList<>(List.of(
                "--server.port=0",
                "--spring.main.banner-mode=off",
                "--logging.level.root=WARN",
                "--logging.level.dev.ngb=INFO",
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + PostgresContainerConfig.APP_ROLE,
                "--spring.datasource.password=" + PostgresContainerConfig.APP_PASSWORD,
                "--spring.datasource.hikari.maximum-pool-size=2",
                "--spring.datasource.hikari.minimum-idle=0",
                "--spring.flyway.enabled=false",
                "--billing.secrets.master-key=" + PostgresContainerConfig.TEST_MASTER_KEY,
                "--billing.tenancy.base-domain=localhost",
                "--billing.jobs.enabled=" + jobs,
                "--billing.payments.jobs-enabled=false",
                "--billing.invoicing.cycle-delay=PT0.5S",
                "--billing.invoicing.cycle-batch=10",
                "--billing.invoicing.renewal-delay=PT0.5S",
                "--billing.invoicing.renewal-batch=10",
                "--billing.outbox.poll-delay=PT0.2S",
                "--billing.outbox.batch-size=10",
                "--billing.webhooks.poll-delay=PT0.2S",
                "--billing.webhooks.batch-size=10",
                "--billing.webhooks.lease=PT1S",
                "--billing.webhooks.backoff-base=PT0.1S",
                "--billing.webhooks.backoff-cap=PT1S",
                "--billing.webhooks.max-attempts=5",
                "--billing.webhooks.allow-insecure-targets=true",
                "--billing.jobs.verify-ledger-initial-delay=PT1H",
                "--billing.jobs.expire-reservations-delay=PT1H",
                "--billing.rate-limit.cleanup-delay=PT1H",
                "--spring.task.scheduling.shutdown.await-termination=false"));
        return new SpringApplicationBuilder(Application.class).profiles(profile).run(args.toArray(String[]::new));
    }

    private static void await(Duration timeout, BooleanSupplier condition) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for the workers", e);
            }
        }
        assertThat(condition.getAsBoolean()).as("condition within " + timeout).isTrue();
    }

    /** Holds the first delivery long enough for the test to stop the worker that is sending it. */
    private static final class SlowReceiver implements AutoCloseable {

        private final HttpServer server;
        private final CountDownLatch first = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger requests = new AtomicInteger();
        private final ExecutorService executor = Executors.newFixedThreadPool(4);

        SlowReceiver() throws IOException {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/hook", exchange -> {
                try {
                    exchange.getRequestBody().readAllBytes();
                    requests.incrementAndGet();
                    first.countDown();
                    release.await(20, TimeUnit.SECONDS);
                    exchange.sendResponseHeaders(200, -1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            });
            server.setExecutor(executor);
            server.start();
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
        }

        boolean awaitFirstRequest() throws InterruptedException {
            return first.await(30, TimeUnit.SECONDS);
        }

        int requests() {
            return requests.get();
        }

        void release() {
            release.countDown();
        }

        @Override
        public void close() {
            release.countDown();
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
