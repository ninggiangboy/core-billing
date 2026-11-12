package dev.ngb.core_billing.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core.event.DomainEvent;
import dev.ngb.core.event.EventEnvelope;
import dev.ngb.core.event.EventPublisher;
import dev.ngb.core.id.TenantId;
import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.core_billing.Application;
import dev.ngb.core_billing.boot.infrastructure.messaging.OutboxRelay;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.support.OwnerDatabase;
import dev.ngb.core_billing.support.PostgresContainerConfig;
import dev.ngb.core_billing.support.ScopeTestSupport;
import dev.ngb.core_billing.support.TestEvent;
import dev.ngb.core_billing.support.TestReceiver;
import dev.ngb.core_billing.webhook.adapter.delivery.messaging.WebhookEventListener;
import dev.ngb.core_billing.webhook.application.delivery.command.DeliverWebhooksUseCase;
import dev.ngb.core_billing.webhook.application.delivery.command.ManageDeliveryUseCase;
import dev.ngb.core_billing.webhook.application.delivery.port.WebhookDeliveryRepository;
import dev.ngb.core_billing.webhook.application.delivery.query.DeliveryQueries;
import dev.ngb.core_billing.webhook.application.delivery.query.WebhookDeliveryView;
import dev.ngb.core_billing.webhook.application.endpoint.command.CreateEndpointUseCase;
import dev.ngb.core_billing.webhook.application.endpoint.command.ManageEndpointUseCase;
import dev.ngb.core_billing.webhook.application.endpoint.query.EndpointQueries;
import dev.ngb.core_billing.webhook.domain.delivery.DeliveryAttempt;
import dev.ngb.core_billing.webhook.domain.delivery.DeliveryStatus;
import dev.ngb.core_billing.webhook.domain.delivery.WebhookDelivery;
import dev.ngb.core_billing.webhook.domain.delivery.WebhookDeliveryId;
import dev.ngb.core_billing.webhook.domain.endpoint.EndpointStatus;
import dev.ngb.core_billing.webhook.domain.endpoint.WebhookEndpoint;
import dev.ngb.core_billing.webhook.domain.endpoint.WebhookEndpointId;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The webhook path against real PostgreSQL: outbox, queued deliveries, signed sends, retries, the dead state and resends. */
@SpringBootTest(classes = Application.class)
@Import(PostgresContainerConfig.class)
class WebhookDeliveryIntegrationTest {

    private static final Audit AUDIT = new Audit("user/test", "req-1");

    @Autowired ScopeTestSupport scopes;
    @Autowired OwnerDatabase owner;
    @Autowired UnitOfWork unitOfWork;
    @Autowired EventPublisher events;
    @Autowired OutboxRelay relay;
    @Autowired DeliverWebhooksUseCase deliver;
    @Autowired CreateEndpointUseCase createEndpoint;
    @Autowired ManageEndpointUseCase endpointAdmin;
    @Autowired ManageDeliveryUseCase deliveryAdmin;
    @Autowired EndpointQueries endpointQueries;
    @Autowired DeliveryQueries deliveryQueries;
    @Autowired WebhookDeliveryRepository deliveryRepository;
    @Autowired WebhookEventListener listener;
    @Autowired io.micrometer.core.instrument.MeterRegistry meterRegistry;

    private final JsonMapper json = JsonMapper.builder().build();

    private record Registered(TenantId tenant, WebhookEndpoint endpoint, String secret) {}

    private Registered register(TenantId tenant, String url, String... types) {
        CreateEndpointUseCase.Result result = scopes.inTenant(tenant, () -> createEndpoint.execute(
                new CreateEndpointUseCase.Command(url, "test endpoint", List.of(types), AUDIT)));
        return new Registered(tenant, result.endpoint(), result.signingSecret());
    }

    private DomainEvent publish(TenantId tenant, String type) {
        UUID id = UUID.randomUUID();
        TestEvent event = new TestEvent(id, tenant, type, "test/" + id, Instant.now(), Map.of("answer", 42));
        scopes.runInTenant(tenant, () -> unitOfWork.transactional(() -> events.publish(event)));
        return event;
    }

    private OutboxRelay.Result relay(TenantId tenant) {
        return scopes.inTenant(tenant, relay::relayDue);
    }

    private DeliverWebhooksUseCase.Round send(TenantId tenant) {
        return scopes.inTenant(tenant, deliver::runOnce);
    }

    private void makeDue(UUID deliveryId) {
        owner.jdbc().update("update webhook_deliveries set next_attempt_at = now() - interval '1 second' where id = ?", deliveryId);
    }

    private List<WebhookDeliveryView> deliveriesOf(TenantId tenant, WebhookEndpointId endpoint) {
        return scopes.inTenant(tenant, () -> deliveryQueries.list(null, endpoint.value(), null, 200));
    }

    /** The receiver-side recipe from contracts/events/README.md, written independently of the production signer. */
    private static boolean verifies(String secret, TestReceiver.Received request, long nowSeconds) throws Exception {
        long timestamp = Long.parseLong(request.header("Billing-Timestamp"));
        if (Math.abs(nowSeconds - timestamp) > 300) {
            return false;
        }
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
        mac.update(request.body().getBytes(StandardCharsets.UTF_8));
        String expected = "v1=" + java.util.HexFormat.of().formatHex(mac.doFinal());
        return java.security.MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), request.header("Billing-Signature").getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void anEventFlowsFromTheOutboxToASignedDeliveryOfTheSubscribedTypesOnly() throws Exception {
        TenantId tenant = scopes.newTenant().id();
        try (TestReceiver receiver = new TestReceiver()) {
            Registered hook = register(tenant, receiver.url(), "charge.succeeded");
            DomainEvent wanted = publish(tenant, "charge.succeeded");
            publish(tenant, "charge.failed");

            assertThat(relay(tenant).delivered()).as("both outbox rows are relayed").isEqualTo(2);
            assertThat(deliveriesOf(tenant, hook.endpoint().id())).as("only the subscribed type is queued").hasSize(1);

            assertThat(send(tenant)).isEqualTo(new DeliverWebhooksUseCase.Round(1, 1, 0, 0));

            assertThat(receiver.requests()).hasSize(1);
            TestReceiver.Received request = receiver.requests().get(0);
            assertThat(request.header("Billing-Event-Id")).isEqualTo(wanted.eventId().toString());
            assertThat(request.header("Billing-Event-Type")).isEqualTo("charge.succeeded");
            assertThat(request.header("Billing-Key-Id")).isEqualTo(hook.endpoint().keyId());
            assertThat(request.header("Billing-Attempt")).isEqualTo("1");
            assertThat(request.header("Content-Type")).startsWith("application/json");
            assertThat(verifies(hook.secret(), request, Instant.now().getEpochSecond())).as("the documented recipe accepts the delivery").isTrue();

            JsonNode body = json.readTree(request.body());
            assertThat(body.get("id").asString()).isEqualTo(wanted.eventId().toString());
            assertThat(body.get("type").asString()).isEqualTo("charge.succeeded");
            assertThat(body.get("tenantId").asString()).isEqualTo(tenant.toString());
            assertThat(body.get("data").get("answer").asInt()).isEqualTo(42);

            WebhookDeliveryView done = deliveriesOf(tenant, hook.endpoint().id()).get(0);
            assertThat(done.status()).isEqualTo(DeliveryStatus.SUCCEEDED);
            assertThat(done.attempts()).isEqualTo(1);
            assertThat(scopes.inTenant(tenant, () -> deliveryQueries.get(done.id())).attempts()).singleElement()
                    .satisfies(a -> assertThat(a.statusCode()).isEqualTo(200));
        }
    }

    @Test
    void everySuccessfulDeliveryRecordsOneLagSample() throws Exception {
        TenantId tenant = scopes.newTenant().id();
        io.micrometer.core.instrument.Timer timer = meterRegistry.timer("billing.webhook.lag");
        long samplesBefore = timer.count();
        double timeBefore = timer.totalTime(TimeUnit.NANOSECONDS);
        try (TestReceiver receiver = new TestReceiver()) {
            register(tenant, receiver.url(), "*");
            publish(tenant, "charge.succeeded");
            relay(tenant);

            assertThat(send(tenant)).isEqualTo(new DeliverWebhooksUseCase.Round(1, 1, 0, 0));
        }

        assertThat(timer.count()).as("one sample per successful delivery").isEqualTo(samplesBefore + 1);
        assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).as("with a non-zero wait").isGreaterThan(timeBefore);
    }

    @Test
    void theSignatureRejectsAStaleTimestampAndATamperedBody() throws Exception {
        TenantId tenant = scopes.newTenant().id();
        try (TestReceiver receiver = new TestReceiver()) {
            Registered hook = register(tenant, receiver.url(), "*");
            publish(tenant, "charge.succeeded");
            relay(tenant);
            send(tenant);
            TestReceiver.Received request = receiver.requests().get(0);
            long now = Instant.now().getEpochSecond();

            assertThat(verifies(hook.secret(), request, now)).isTrue();
            assertThat(verifies(hook.secret(), request, now + 3600)).as("an old delivery is replay-protected").isFalse();
            TestReceiver.Received tampered = new TestReceiver.Received(request.headers(), request.body().replace("42", "43"));
            assertThat(verifies(hook.secret(), tampered, now)).isFalse();
            assertThat(verifies(hook.secret() + "x", request, now)).as("another secret").isFalse();
        }
    }

    @Test
    void aFailingReceiverIsRetriedThenMarkedDeadAndAResendStartsAFreshBudget() throws Exception {
        TenantId tenant = scopes.newTenant().id();
        try (TestReceiver receiver = new TestReceiver().answerAlways(500)) {
            Registered hook = register(tenant, receiver.url(), "*");
            publish(tenant, "charge.succeeded");
            relay(tenant);
            WebhookDeliveryView queued = deliveriesOf(tenant, hook.endpoint().id()).get(0);

            assertThat(send(tenant)).isEqualTo(new DeliverWebhooksUseCase.Round(1, 0, 1, 0));
            WebhookDeliveryView afterFirst = deliveriesOf(tenant, hook.endpoint().id()).get(0);
            assertThat(afterFirst.status()).isEqualTo(DeliveryStatus.PENDING);
            assertThat(afterFirst.nextAttemptAt()).as("backed off into the future").isAfter(Instant.now().plusSeconds(10));
            assertThat(send(tenant)).as("not due yet").isEqualTo(DeliverWebhooksUseCase.Round.NONE);

            makeDue(queued.id());
            assertThat(send(tenant).retrying()).isEqualTo(1);
            makeDue(queued.id());
            assertThat(send(tenant)).as("third and last attempt").isEqualTo(new DeliverWebhooksUseCase.Round(1, 0, 0, 1));

            WebhookDeliveryView dead = deliveriesOf(tenant, hook.endpoint().id()).get(0);
            assertThat(dead.status()).isEqualTo(DeliveryStatus.DEAD);
            assertThat(dead.attempts()).isEqualTo(3);
            assertThat(dead.lastStatusCode()).isEqualTo(500);
            assertThat(dead.completedAt()).isNotNull();
            makeDue(queued.id());
            assertThat(send(tenant)).as("a dead delivery is never sent again by itself").isEqualTo(DeliverWebhooksUseCase.Round.NONE);
            assertThat(scopes.inTenant(tenant, () -> deliveryQueries.get(dead.id())).attempts()).extracting(a -> a.statusCode()).containsExactly(500, 500, 500);

            receiver.answerAlways(200);
            WebhookDelivery resent = scopes.inTenant(tenant, () -> deliveryAdmin.resend(AUDIT, new WebhookDeliveryId(dead.id())));
            assertThat(resent.id()).isNotEqualTo(new WebhookDeliveryId(dead.id()));
            assertThat(resent.resendOf()).isEqualTo(new WebhookDeliveryId(dead.id()));
            assertThat(resent.eventId()).isEqualTo(dead.eventId());
            assertThat(resent.body()).as("byte-identical to the stored event").isEqualTo(dead.body());
            assertThat(send(tenant)).isEqualTo(new DeliverWebhooksUseCase.Round(1, 1, 0, 0));

            assertThat(deliveriesOf(tenant, hook.endpoint().id())).extracting(WebhookDeliveryView::status)
                    .containsExactlyInAnyOrder(DeliveryStatus.DEAD, DeliveryStatus.SUCCEEDED);
            assertThat(scopes.inTenant(tenant, () -> deliveryQueries.get(dead.id())).delivery().status()).as("history is kept").isEqualTo(DeliveryStatus.DEAD);
        }
    }

    @Test
    void onlyAFinishedDeliveryCanBeResent() throws Exception {
        TenantId tenant = scopes.newTenant().id();
        try (TestReceiver receiver = new TestReceiver()) {
            Registered hook = register(tenant, receiver.url(), "*");
            publish(tenant, "charge.succeeded");
            relay(tenant);
            WebhookDeliveryView pending = deliveriesOf(tenant, hook.endpoint().id()).get(0);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> scopes.inTenant(tenant, () -> deliveryAdmin.resend(AUDIT, new WebhookDeliveryId(pending.id()))))
                    .isInstanceOf(dev.ngb.core_billing.platform.api.error.ConflictException.class);
        }
    }

    @Test
    void aWorkerThatDiesMidDeliveryLeavesTheRowRetryable() throws Exception {
        TenantId tenant = scopes.newTenant().id();
        try (TestReceiver receiver = new TestReceiver()) {
            Registered hook = register(tenant, receiver.url(), "*");
            publish(tenant, "charge.succeeded");
            relay(tenant);
            WebhookDeliveryView queued = deliveriesOf(tenant, hook.endpoint().id()).get(0);

            // A worker claims the row and dies before recording anything.
            var claimed = scopes.inTenant(tenant, () -> unitOfWork.transactional(
                    () -> deliveryRepository.claimDue(Instant.now(), 10, java.time.Duration.ofMinutes(2))));
            assertThat(claimed).hasSize(1);
            assertThat(deliveriesOf(tenant, hook.endpoint().id()).get(0).status()).isEqualTo(DeliveryStatus.IN_PROGRESS);

            assertThat(send(tenant)).as("while the lease holds, nobody else takes it").isEqualTo(DeliverWebhooksUseCase.Round.NONE);

            makeDue(queued.id()); // the lease runs out
            assertThat(send(tenant)).isEqualTo(new DeliverWebhooksUseCase.Round(1, 1, 0, 0));
            WebhookDeliveryView done = deliveriesOf(tenant, hook.endpoint().id()).get(0);
            assertThat(done.status()).isEqualTo(DeliveryStatus.SUCCEEDED);
            assertThat(done.attempts()).as("the lost attempt counts").isEqualTo(2);
            assertThat(receiver.requests()).hasSize(1);
        }
    }

    @Test
    void aDeliveryWhoseWorkerWasLostOnTheFinalAttemptIsDeadNotResent() throws Exception {
        TenantId tenant = scopes.newTenant().id();
        try (TestReceiver receiver = new TestReceiver()) {
            Registered hook = register(tenant, receiver.url(), "*");
            publish(tenant, "charge.succeeded");
            relay(tenant);
            WebhookDeliveryView queued = deliveriesOf(tenant, hook.endpoint().id()).get(0);
            owner.jdbc().update("update webhook_deliveries set status = 'IN_PROGRESS', attempts = 3, next_attempt_at = now() - interval '1 second' where id = ?",
                    queued.id());

            assertThat(send(tenant)).isEqualTo(DeliverWebhooksUseCase.Round.NONE);
            assertThat(deliveriesOf(tenant, hook.endpoint().id()).get(0).status()).isEqualTo(DeliveryStatus.DEAD);
            assertThat(receiver.requests()).isEmpty();
        }
    }

    @Test
    void aWorkerThatLostItsLeaseCannotOverwriteTheOneThatTookOver() throws Exception {
        TenantId tenant = scopes.newTenant().id();
        try (TestReceiver receiver = new TestReceiver()) {
            Registered hook = register(tenant, receiver.url(), "*");
            publish(tenant, "charge.succeeded");
            relay(tenant);
            WebhookDeliveryView queued = deliveriesOf(tenant, hook.endpoint().id()).get(0);

            var slow = scopes.inTenant(tenant, () -> unitOfWork.transactional(() -> deliveryRepository.claimDue(Instant.now(), 10, java.time.Duration.ofMinutes(2))));
            makeDue(queued.id());
            assertThat(send(tenant).succeeded()).isEqualTo(1); // the second worker finishes first

            // The slow worker finally reports its stale attempt 1 as a failure.
            scopes.runInTenant(tenant, () -> unitOfWork.transactional(() -> deliveryRepository.recordAttempt(
                    slow.get(0).delivery().id(), 1,
                    new dev.ngb.core_billing.webhook.domain.delivery.DeliveryAttempt(1, 500, null, "late", 5, Instant.now()), DeliveryStatus.PENDING, Instant.now(), null)));

            assertThat(deliveriesOf(tenant, hook.endpoint().id()).get(0).status()).as("the newer outcome stands").isEqualTo(DeliveryStatus.SUCCEEDED);
            assertThat(scopes.inTenant(tenant, () -> deliveryQueries.get(queued.id())).attempts()).as("but the late attempt is still logged").hasSize(2);
        }
    }

    @Test
    void concurrentWorkersNeverDeliverTheSameRowTwice() throws Exception {
        TenantId tenant = scopes.newTenant().id();
        int events = 60;
        try (TestReceiver receiver = new TestReceiver()) {
            Registered hook = register(tenant, receiver.url(), "*");
            Set<String> published = new HashSet<>();
            for (int i = 0; i < events; i++) {
                published.add(publish(tenant, "charge.succeeded").eventId().toString());
            }
            while (relay(tenant).delivered() > 0) {
                // drain the outbox
            }
            assertThat(deliveriesOf(tenant, hook.endpoint().id())).hasSize(events);

            ExecutorService pool = Executors.newFixedThreadPool(5);
            try {
                List<CompletableFuture<Integer>> workers = new ArrayList<>();
                for (int w = 0; w < 5; w++) {
                    workers.add(CompletableFuture.supplyAsync(() -> {
                        int sent = 0;
                        while (true) {
                            int round = send(tenant).claimed();
                            if (round == 0) {
                                return sent;
                            }
                            sent += round;
                        }
                    }, pool));
                }
                int total = 0;
                for (CompletableFuture<Integer> worker : workers) {
                    total += worker.get(60, TimeUnit.SECONDS);
                }
                assertThat(total).isEqualTo(events);
            } finally {
                pool.shutdownNow();
            }

            List<String> seen = receiver.requests().stream().map(r -> r.header("Billing-Event-Id")).toList();
            assertThat(seen).hasSize(events).doesNotHaveDuplicates();
            assertThat(new HashSet<>(seen)).isEqualTo(published);
            assertThat(owner.count("select count(*) from webhook_delivery_attempts where tenant_id = ?", tenant.value())).isEqualTo(events);
        }
    }

    @Test
    void relayingTheSameEventTwiceQueuesOneDeliveryPerEndpoint() throws Exception {
        TenantId tenant = scopes.newTenant().id();
        try (TestReceiver receiver = new TestReceiver()) {
            Registered hook = register(tenant, receiver.url(), "*");
            EventEnvelope event = new EventEnvelope(UUID.randomUUID(), tenant, "charge.succeeded", "charge/1", Instant.now(), Map.of("a", 1));

            scopes.runInTenant(tenant, () -> listener.on(event));
            scopes.runInTenant(tenant, () -> listener.on(event));

            assertThat(deliveriesOf(tenant, hook.endpoint().id())).hasSize(1);
        }
    }

    @Test
    void aDisabledEndpointPausesDeliveryAndResumingSendsTheQueue() throws Exception {
        TenantId tenant = scopes.newTenant().id();
        try (TestReceiver receiver = new TestReceiver()) {
            Registered hook = register(tenant, receiver.url(), "*");
            scopes.runInTenant(tenant, () -> endpointAdmin.update(AUDIT, hook.endpoint().id(), new ManageEndpointUseCase.Change(null, null, null, EndpointStatus.DISABLED)));
            publish(tenant, "charge.succeeded");
            relay(tenant);
            assertThat(deliveriesOf(tenant, hook.endpoint().id())).as("a disabled endpoint is not subscribed to anything").isEmpty();

            scopes.runInTenant(tenant, () -> endpointAdmin.update(AUDIT, hook.endpoint().id(), new ManageEndpointUseCase.Change(null, null, null, EndpointStatus.ACTIVE)));
            publish(tenant, "charge.succeeded");
            relay(tenant);
            scopes.runInTenant(tenant, () -> endpointAdmin.update(AUDIT, hook.endpoint().id(), new ManageEndpointUseCase.Change(null, null, null, EndpointStatus.DISABLED)));
            assertThat(send(tenant)).as("queued but the endpoint is paused").isEqualTo(DeliverWebhooksUseCase.Round.NONE);
            scopes.runInTenant(tenant, () -> endpointAdmin.update(AUDIT, hook.endpoint().id(), new ManageEndpointUseCase.Change(null, null, null, EndpointStatus.ACTIVE)));
            assertThat(send(tenant).succeeded()).isEqualTo(1);
        }
    }

    @Test
    void rotatingTheSecretSignsLaterDeliveriesWithTheNewKeyOnly() throws Exception {
        TenantId tenant = scopes.newTenant().id();
        try (TestReceiver receiver = new TestReceiver()) {
            Registered hook = register(tenant, receiver.url(), "*");
            ManageEndpointUseCase.Rotated rotated = scopes.inTenant(tenant, () -> endpointAdmin.rotateSecret(AUDIT, hook.endpoint().id()));
            assertThat(rotated.signingSecret()).isNotEqualTo(hook.secret());
            assertThat(rotated.endpoint().keyId()).isNotEqualTo(hook.endpoint().keyId());

            publish(tenant, "charge.succeeded");
            relay(tenant);
            send(tenant);
            TestReceiver.Received request = receiver.requests().get(0);
            assertThat(request.header("Billing-Key-Id")).isEqualTo(rotated.endpoint().keyId());
            assertThat(verifies(rotated.signingSecret(), request, Instant.now().getEpochSecond())).isTrue();
            assertThat(verifies(hook.secret(), request, Instant.now().getEpochSecond())).isFalse();
        }
    }

    @Test
    void theSecretIsStoredOnlyAsCiphertext() {
        TenantId tenant = scopes.newTenant().id();
        Registered hook = register(tenant, "https://hooks.example.com/x", "*");
        String stored = owner.jdbc().queryForObject("select secret_ciphertext from webhook_endpoints where id = ?", String.class, hook.endpoint().id().value());
        assertThat(stored).startsWith("v1.").doesNotContain(hook.secret()).doesNotContain(hook.secret().substring(6));
        assertThat(hook.secret()).startsWith("whsec_");
        assertThat(scopes.inTenant(tenant, () -> endpointQueries.get(hook.endpoint().id().value())).secretCiphertext()).isEqualTo(stored);
    }

    @Test
    void anotherTenantsEventsNeverReachAnEndpoint() throws Exception {
        TenantId acme = scopes.newTenant().id();
        TenantId globex = scopes.newTenant().id();
        try (TestReceiver receiver = new TestReceiver()) {
            Registered hook = register(acme, receiver.url(), "*");
            publish(globex, "charge.succeeded");
            relay(globex);
            relay(acme);
            send(acme);
            send(globex);
            assertThat(receiver.requests()).isEmpty();
            assertThat(deliveriesOf(acme, hook.endpoint().id())).isEmpty();
        }
    }

    @Test
    void endpointRulesAreEnforced() {
        TenantId tenant = scopes.newTenant().id();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> register(tenant, "https://hooks.example.com/x", "no.such-event"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unknown event type");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> register(tenant, "ftp://hooks.example.com/x", "*"))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> register(tenant, "https://hooks.example.com/x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(register(tenant, "https://hooks.example.com/x", "charge.succeeded", "reservation.expired").endpoint().eventTypes())
                .containsExactly("charge.succeeded", "reservation.expired");
        assertThat(owner.count("select count(*) from audit_log where tenant_id = ? and action = 'webhook-endpoint.created'", tenant.value())).isEqualTo(1);
    }

    @Test
    void deliveryHistoryFiltersByStatusAndPages() throws Exception {
        TenantId tenant = scopes.newTenant().id();
        try (TestReceiver receiver = new TestReceiver()) {
            Registered hook = register(tenant, receiver.url(), "*");
            for (int i = 0; i < 5; i++) {
                publish(tenant, "charge.succeeded");
            }
            while (relay(tenant).delivered() > 0) {
                // drain
            }
            send(tenant);
            assertThat(scopes.inTenant(tenant, () -> deliveryQueries.list(DeliveryStatus.SUCCEEDED, hook.endpoint().id().value(), null, 200))).hasSize(5);
            assertThat(scopes.inTenant(tenant, () -> deliveryQueries.list(DeliveryStatus.DEAD, hook.endpoint().id().value(), null, 200))).isEmpty();
            List<WebhookDeliveryView> first = scopes.inTenant(tenant, () -> deliveryQueries.list(null, hook.endpoint().id().value(), null, 2));
            List<WebhookDeliveryView> second = scopes.inTenant(tenant, () -> deliveryQueries.list(null, hook.endpoint().id().value(), first.get(1).id(), 2));
            assertThat(first).hasSize(2);
            assertThat(second).hasSize(2);
            assertThat(second.get(0).id()).isLessThan(first.get(1).id());
            WebhookDeliveryId unknown = new WebhookDeliveryId(UUID.randomUUID());
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> scopes.inTenant(tenant, () -> deliveryQueries.get(unknown.value())))
                    .isInstanceOf(dev.ngb.core_billing.platform.api.error.NotFoundException.class);
        }
    }
}
