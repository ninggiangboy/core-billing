package dev.ngb.core_billing.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.ngb.core_billing.Application;
import dev.ngb.core_billing.platform.application.user.command.BootstrapPlatformAdminUseCase;
import dev.ngb.core_billing.support.FakePaymentProvider;
import dev.ngb.core_billing.support.FakePaymentProviderConfig;
import dev.ngb.core_billing.support.HttpWorld;
import dev.ngb.core_billing.support.HttpWorld.Account;
import dev.ngb.core_billing.support.OwnerDatabase;
import dev.ngb.core_billing.support.PostgresContainerConfig;
import dev.ngb.core_billing.support.RealKeycloakConfig;
import dev.ngb.core_billing.support.ScopeTestSupport;
import dev.ngb.core_billing.support.StripeEvents;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The payments API over HTTP with real tokens: who may connect Stripe, what a read never returns, top-ups and
 * refunds, and isolation between tenants. Stripe's network calls are answered by an in-memory provider.
 */
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, RealKeycloakConfig.class, FakePaymentProviderConfig.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PaymentHttpIntegrationTest {

    private static final String SECRET_KEY = "sk_test_http_secret_key_0123456789";
    private static final String WEBHOOK_SECRET = "whsec_http_webhook_secret_0123456789";

    @Autowired MockMvc mvc;
    @Autowired BootstrapPlatformAdminUseCase bootstrap;
    @Autowired ScopeTestSupport scopes;
    @Autowired OwnerDatabase owner;
    @Autowired FakePaymentProvider stripe;
    @Value("${billing.tenancy.base-domain}") String baseDomain;

    private final JsonMapper json = JsonMapper.builder().build();
    HttpWorld world;
    Account acme;
    Account globex;
    String systemKey;
    String viewerToken;
    String customerId;
    String walletId;

    @BeforeAll
    void createWorld() {
        world = new HttpWorld(mvc, bootstrap, scopes, baseDomain);
        acme = world.newTenant();
        globex = world.newTenant();
        String systemId = world.body(world.call(HttpMethod.POST, acme.host(), acme.adminToken(), "/api/v1/tenant-systems", HttpWorld.Map2.of("name", "Sample")))
                .get("id").asString();
        systemKey = world.body(world.call(HttpMethod.POST, acme.host(), acme.adminToken(), "/api/v1/tenant-systems/" + systemId + "/credentials",
                HttpWorld.Map2.of("label", "x", "scopes", List.of("payment:create", "payment:read", "wallet:manage", "wallet:read", "customer:write", "customer:read"))))
                .get("key").asString();
        String viewerRole = world.createRole(acme, "Viewer", java.util.Set.of("payment:read", "stripe:read"));
        viewerToken = world.newUserToken(acme, java.util.Set.of(viewerRole));
        customerId = world.body(call(HttpMethod.POST, acme, systemKey, "/api/v1/customers", "cust-" + UUID.randomUUID(),
                HttpWorld.Map2.of("externalUserId", "u-" + UUID.randomUUID(), "displayName", "Payer"))).get("id").asString();
        walletId = world.body(call(HttpMethod.POST, acme, systemKey, "/api/v1/wallets", null,
                HttpWorld.Map2.of("customerId", customerId, "currency", "USD", "mode", "PREPAID"))).get("id").asString();
        connectDirect();
    }

    @BeforeEach
    void resetStripe() {
        stripe.reset();
    }

    private MvcResult call(HttpMethod method, Account account, String token, String path, String idempotencyKey, Object payload) {
        try {
            var builder = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(method, path).header("Host", account.host());
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
            }
            if (idempotencyKey != null) {
                builder.header("Idempotency-Key", idempotencyKey);
            }
            if (payload != null) {
                builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload));
            }
            return mvc.perform(builder).andReturn();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode connectDirect() {
        return world.body(call(HttpMethod.PUT, acme, acme.adminToken(), "/api/v1/stripe/configuration/direct", null,
                HttpWorld.Map2.of("secretKey", SECRET_KEY, "publishableKey", "pk_test_x", "webhookSecret", WEBHOOK_SECRET)));
    }

    private JsonNode topUp(long amount, String key) {
        return world.body(call(HttpMethod.POST, acme, systemKey, "/api/v1/payments/top-ups", key,
                HttpWorld.Map2.of("walletId", walletId, "amount", amount, "currency", "USD")));
    }

    private void deliverPaid(JsonNode payment, String intentId) throws Exception {
        String configId = world.body(call(HttpMethod.GET, acme, acme.adminToken(), "/api/v1/stripe/configuration", null, null)).get("id").asString();
        String session = owner.jdbc().queryForObject("select checkout_session_id from payments where id = ?", String.class,
                UUID.fromString(payment.get("id").asString()));
        String body = StripeEvents.paidCheckout(StripeEvents.newEventId(), null, session, intentId,
                payment.get("id").asString(), payment.get("amount").get("amount").asLong());
        MvcResult result = mvc.perform(post("/stripe/webhooks/direct/" + configId).header("Host", "platform." + baseDomain)
                .contentType(MediaType.APPLICATION_JSON).content(body.getBytes(StandardCharsets.UTF_8))
                .header("Stripe-Signature", StripeEvents.sign(WEBHOOK_SECRET, body))).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void theConfigurationNeverReturnsASecretAndIsEncryptedAtRest() throws Exception {
        JsonNode configuration = connectDirect();

        String text = configuration.toString();
        assertThat(text).doesNotContain(SECRET_KEY).doesNotContain(WEBHOOK_SECRET).doesNotContain("secretKey");
        assertThat(configuration.get("mode").asString()).isEqualTo("DIRECT");
        assertThat(configuration.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(configuration.get("webhookSecretSet").asBoolean()).isTrue();
        assertThat(configuration.get("webhookUrl").asString()).endsWith("/stripe/webhooks/direct/" + configuration.get("id").asString());
        assertThat(configuration.get("publishableKey").asString()).isEqualTo("pk_test_x");

        String read = world.call(HttpMethod.GET, acme.host(), acme.adminToken(), "/api/v1/stripe/configuration", null).getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(read).doesNotContain(SECRET_KEY).doesNotContain(WEBHOOK_SECRET);

        String stored = owner.jdbc().queryForObject("select secret_key_ciphertext || webhook_secret_ciphertext from stripe_configurations where id = ?", String.class,
                UUID.fromString(configuration.get("id").asString()));
        assertThat(stored).startsWith("v1.").doesNotContain(SECRET_KEY).doesNotContain(WEBHOOK_SECRET);
        assertThat(stripe.keysVerified).contains(SECRET_KEY);
    }

    @Test
    void secretsNeverReachTheLogs() throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);
        try {
            connectDirect();
            world.call(HttpMethod.PUT, acme.host(), acme.adminToken(), "/api/v1/stripe/configuration/direct",
                    HttpWorld.Map2.of("secretKey", "sk_test_bad_key_for_the_failure_path", "webhookSecret", WEBHOOK_SECRET));
            JsonNode payment = topUp(1500, "log-1");
            deliverPaid(payment, "pi_log_" + UUID.randomUUID().toString().substring(0, 8));
        } finally {
            root.detachAppender(logs);
        }
        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(m -> m.contains(SECRET_KEY) || m.contains(WEBHOOK_SECRET) || m.contains("sk_test_bad_key"));
        assertThat(logs.list).filteredOn(e -> e.getThrowableProxy() != null)
                .extracting(e -> e.getThrowableProxy().getMessage()).noneMatch(m -> m != null && (m.contains(SECRET_KEY) || m.contains(WEBHOOK_SECRET)));
    }

    @Test
    void aKeyStripeRejectsLeavesTheCurrentConfigurationInPlace() {
        JsonNode before = world.body(call(HttpMethod.GET, acme, acme.adminToken(), "/api/v1/stripe/configuration", null, null));

        MvcResult result = call(HttpMethod.PUT, acme, acme.adminToken(), "/api/v1/stripe/configuration/direct", null,
                HttpWorld.Map2.of("secretKey", "sk_test_bad_key_for_rejection_x"));

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(world.body(call(HttpMethod.GET, acme, acme.adminToken(), "/api/v1/stripe/configuration", null, null)).get("id").asString())
                .isEqualTo(before.get("id").asString());
    }

    @Test
    void malformedKeysAreRefusedBeforeStripeIsAsked() {
        assertThat(call(HttpMethod.PUT, acme, acme.adminToken(), "/api/v1/stripe/configuration/direct", null,
                HttpWorld.Map2.of("secretKey", "not-a-stripe-key-at-all-12345")).getResponse().getStatus()).isEqualTo(400);
        assertThat(call(HttpMethod.PUT, acme, acme.adminToken(), "/api/v1/stripe/configuration/webhook-secret", null,
                HttpWorld.Map2.of("webhookSecret", "nope")).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void anotherTenantCannotLinkTheSameStripeAccount() {
        MvcResult result = call(HttpMethod.PUT, globex, globex.adminToken(), "/api/v1/stripe/configuration/direct", null,
                HttpWorld.Map2.of("secretKey", SECRET_KEY, "webhookSecret", WEBHOOK_SECRET));

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(call(HttpMethod.GET, globex, globex.adminToken(), "/api/v1/stripe/configuration", null, null).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void onlyAnAdministratorMayConnectStripeOrRefund() {
        assertThat(call(HttpMethod.PUT, acme, viewerToken, "/api/v1/stripe/configuration/direct", null,
                HttpWorld.Map2.of("secretKey", SECRET_KEY)).getResponse().getStatus()).as("a viewer").isEqualTo(403);
        assertThat(call(HttpMethod.PUT, acme, systemKey, "/api/v1/stripe/configuration/direct", null,
                HttpWorld.Map2.of("secretKey", SECRET_KEY)).getResponse().getStatus()).as("a tenant system").isEqualTo(403);
        assertThat(call(HttpMethod.GET, acme, systemKey, "/api/v1/stripe/configuration", null, null).getResponse().getStatus()).isEqualTo(403);
        assertThat(call(HttpMethod.GET, acme, viewerToken, "/api/v1/stripe/configuration", null, null).getResponse().getStatus()).as("a viewer may read").isEqualTo(200);
        assertThat(call(HttpMethod.POST, acme, systemKey, "/api/v1/payments/" + UUID.randomUUID() + "/refunds", "k", HttpWorld.Map2.of()).getResponse().getStatus())
                .as("refunds are never granted to a tenant system").isEqualTo(403);
        assertThat(call(HttpMethod.POST, acme, viewerToken, "/api/v1/payments/top-ups", "k",
                HttpWorld.Map2.of("walletId", walletId, "amount", 100, "currency", "USD")).getResponse().getStatus()).as("a viewer cannot top up").isEqualTo(403);
        String anySystem = world.body(call(HttpMethod.POST, acme, acme.adminToken(), "/api/v1/tenant-systems", null, HttpWorld.Map2.of("name", "Another"))).get("id").asString();
        assertThat(call(HttpMethod.POST, acme, acme.adminToken(), "/api/v1/tenant-systems/" + anySystem + "/credentials", null,
                HttpWorld.Map2.of("label", "x", "scopes", List.of("refund:create"))).getResponse().getStatus()).as("the scope cannot be granted to a machine").isEqualTo(400);
    }

    @Test
    void aTenantSystemStartsATopUpAndGetsStripesHostedPageWithoutMovingMoney() throws Exception {
        long before = world.body(call(HttpMethod.GET, acme, systemKey, "/api/v1/wallets/" + walletId, null, null)).get("balance").get("amount").asLong();
        JsonNode payment = topUp(2500, "http-1");

        assertThat(payment.get("status").asString()).isEqualTo("REQUIRES_ACTION");
        assertThat(payment.get("checkoutUrl").asString()).startsWith("https://checkout.stripe.test/");
        assertThat(payment.get("amount").get("amount").asLong()).isEqualTo(2500);
        assertThat(payment.toString()).doesNotContain(SECRET_KEY);
        assertThat(world.body(call(HttpMethod.GET, acme, systemKey, "/api/v1/wallets/" + walletId, null, null)).get("balance").get("amount").asLong())
                .isEqualTo(before);

        MvcResult replay = call(HttpMethod.POST, acme, systemKey, "/api/v1/payments/top-ups", "http-1",
                HttpWorld.Map2.of("walletId", walletId, "amount", 2500, "currency", "USD"));
        assertThat(replay.getResponse().getStatus()).isEqualTo(200);
        assertThat(json.readTree(replay.getResponse().getContentAsString()).get("id").asString()).isEqualTo(payment.get("id").asString());
        assertThat(call(HttpMethod.POST, acme, systemKey, "/api/v1/payments/top-ups", "http-1",
                HttpWorld.Map2.of("walletId", walletId, "amount", 9999, "currency", "USD")).getResponse().getStatus()).isEqualTo(409);
        assertThat(call(HttpMethod.POST, acme, systemKey, "/api/v1/payments/top-ups", null,
                HttpWorld.Map2.of("walletId", walletId, "amount", 100, "currency", "USD")).getResponse().getStatus()).as("a key is required").isEqualTo(400);
    }

    @Test
    void aTopUpThatStripeCannotReachAsksTheCallerToRepeatWithTheSameKey() {
        stripe.failNextCheckout = FakePaymentProvider.unreachable();

        assertThat(call(HttpMethod.POST, acme, systemKey, "/api/v1/payments/top-ups", "http-down",
                HttpWorld.Map2.of("walletId", walletId, "amount", 300, "currency", "USD")).getResponse().getStatus()).isEqualTo(502);
        assertThat(call(HttpMethod.POST, acme, systemKey, "/api/v1/payments/top-ups", "http-down",
                HttpWorld.Map2.of("walletId", walletId, "amount", 300, "currency", "USD")).getResponse().getStatus()).isEqualTo(201);
    }

    /** The same key limit applies here as in every other context that takes an {@code Idempotency-Key}. */
    @Test
    void aKeyOfUpTo255CharactersIsAccepted() {
        String accepted = "k" + "x".repeat(219);
        String tooLong = "k" + "x".repeat(255);

        assertThat(call(HttpMethod.POST, acme, systemKey, "/api/v1/payments/top-ups", accepted,
                HttpWorld.Map2.of("walletId", walletId, "amount", 100, "currency", "USD")).getResponse().getStatus())
                .as("220 characters is within the limit").isEqualTo(201);
        assertThat(call(HttpMethod.POST, acme, systemKey, "/api/v1/payments/top-ups", tooLong,
                HttpWorld.Map2.of("walletId", walletId, "amount", 100, "currency", "USD")).getResponse().getStatus())
                .as("256 characters is over the limit").isEqualTo(400);
    }

    @Test
    void aVerifiedPaymentCreditsTheWalletAndCanBeRefundedThroughTheApi() throws Exception {
        long before = world.body(call(HttpMethod.GET, acme, systemKey, "/api/v1/wallets/" + walletId, null, null)).get("balance").get("amount").asLong();
        JsonNode payment = topUp(4000, "http-flow-" + UUID.randomUUID());
        deliverPaid(payment, "pi_flow_" + UUID.randomUUID().toString().substring(0, 8));

        JsonNode settled = world.body(call(HttpMethod.GET, acme, systemKey, "/api/v1/payments/" + payment.get("id").asString(), null, null));
        assertThat(settled.get("payment").get("status").asString()).isEqualTo("SUCCEEDED");
        assertThat(settled.get("payment").get("checkoutUrl").isNull()).as("a settled payment offers no page to pay").isTrue();
        assertThat(world.body(call(HttpMethod.GET, acme, systemKey, "/api/v1/wallets/" + walletId, null, null)).get("balance").get("amount").asLong())
                .isEqualTo(before + 4000);

        MvcResult refund = call(HttpMethod.POST, acme, acme.adminToken(), "/api/v1/payments/" + payment.get("id").asString() + "/refunds", "http-refund-" + payment.get("id").asString(),
                HttpWorld.Map2.of("amount", 1000, "currency", "USD", "reason", "customer asked"));
        assertThat(refund.getResponse().getStatus()).isEqualTo(201);
        JsonNode refunded = json.readTree(refund.getResponse().getContentAsString());
        assertThat(refunded.get("status").asString()).isEqualTo("PENDING");
        assertThat(world.body(call(HttpMethod.GET, acme, systemKey, "/api/v1/wallets/" + walletId, null, null)).get("balance").get("amount").asLong())
                .as("not debited until Stripe confirms").isEqualTo(before + 4000);

        assertThat(call(HttpMethod.POST, acme, acme.adminToken(), "/api/v1/payments/" + payment.get("id").asString() + "/refunds", "http-refund-too-much-" + payment.get("id").asString(),
                HttpWorld.Map2.of("amount", 3001, "currency", "USD")).getResponse().getStatus()).as("more than is left").isEqualTo(409);
        assertThat(world.body(call(HttpMethod.GET, acme, acme.adminToken(), "/api/v1/refunds/" + refunded.get("id").asString(), null, null)).get("id").asString())
                .isEqualTo(refunded.get("id").asString());
    }

    @Test
    void anotherTenantSeesNoneOfThis() {
        JsonNode payment = topUp(100, "iso-" + UUID.randomUUID());

        assertThat(call(HttpMethod.GET, globex, globex.adminToken(), "/api/v1/payments/" + payment.get("id").asString(), null, null).getResponse().getStatus())
                .isEqualTo(404);
        assertThat(world.body(call(HttpMethod.GET, globex, globex.adminToken(), "/api/v1/payments", null, null)).size()).isZero();
        assertThat(call(HttpMethod.POST, globex, globex.adminToken(), "/api/v1/payments/" + payment.get("id").asString() + "/refunds", "iso-r",
                HttpWorld.Map2.of()).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void connectIsOnlyOfferedWhenThePlatformHasConfiguredIt() {
        assertThat(call(HttpMethod.POST, acme, acme.adminToken(), "/api/v1/stripe/configuration/connect", null,
                HttpWorld.Map2.of("displayName", "Acme Ltd")).getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void anAdministratorSeesWebhookHistoryAndCanRunReconciliation() throws Exception {
        JsonNode payment = topUp(200, "hist-" + UUID.randomUUID());
        deliverPaid(payment, "pi_hist_" + UUID.randomUUID().toString().substring(0, 8));

        JsonNode events = world.body(call(HttpMethod.GET, acme, acme.adminToken(), "/api/v1/stripe/events?status=PROCESSED", null, null));
        assertThat(events.size()).isGreaterThanOrEqualTo(1);
        String eventId = events.get(0).get("id").asString();
        JsonNode detail = world.body(call(HttpMethod.GET, acme, acme.adminToken(), "/api/v1/stripe/events/" + eventId, null, null));
        assertThat(detail.get("history").get(0).get("outcome").asString()).isEqualTo("PROCESSED");
        assertThat(detail.toString()).as("the stored payload is not exposed").doesNotContain("payload");

        JsonNode report = world.body(call(HttpMethod.POST, acme, acme.adminToken(), "/api/v1/stripe/reconcile", null, null));
        assertThat(report.has("checked")).isTrue();
        assertThat(call(HttpMethod.POST, acme, viewerToken, "/api/v1/stripe/reconcile", null, null).getResponse().getStatus()).isEqualTo(403);
        assertThat(call(HttpMethod.GET, acme, viewerToken, "/api/v1/stripe/incidents", null, null).getResponse().getStatus()).isEqualTo(200);
    }
}
