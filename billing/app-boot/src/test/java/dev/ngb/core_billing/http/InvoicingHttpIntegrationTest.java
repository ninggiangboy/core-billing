package dev.ngb.core_billing.http;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core_billing.Application;
import dev.ngb.core_billing.boot.infrastructure.outbox.OutboxRelay;
import dev.ngb.core_billing.invoicing.application.cycle.command.CloseBillingCycleUseCase;
import dev.ngb.core_billing.money.api.charge.ChargeCommand;
import dev.ngb.core_billing.money.api.charge.ChargeResult;
import dev.ngb.core_billing.money.api.charge.ChargeWallet;
import dev.ngb.core_billing.platform.application.user.command.BootstrapPlatformAdminUseCase;
import dev.ngb.core_billing.pricing.PricingTestWorld;
import dev.ngb.core_billing.pricing.api.plan.BillingCycle;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.TenantId;
import dev.ngb.core_billing.support.FakePaymentProvider;
import dev.ngb.core_billing.support.FakePaymentProviderConfig;
import dev.ngb.core_billing.support.HttpWorld;
import dev.ngb.core_billing.support.HttpWorld.Account;
import dev.ngb.core_billing.support.OwnerDatabase;
import dev.ngb.core_billing.support.PostgresContainerConfig;
import dev.ngb.core_billing.support.RealKeycloakConfig;
import dev.ngb.core_billing.support.ScopeTestSupport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
 * Invoices and subscriptions over HTTP with real tokens: who may read, pay, void and subscribe, what a Tenant System
 * is allowed to see, and that no request marks an invoice paid. Stripe is the in-memory provider.
 */
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, RealKeycloakConfig.class, FakePaymentProviderConfig.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvoicingHttpIntegrationTest {

    private static final String SCOPES_TEXT = "invoice:read,invoice:create,subscription:read,subscription:manage,payment:create,payment:read,wallet:manage,"
            + "wallet:read,customer:write,customer:read,charge:create";

    @Autowired MockMvc mvc;
    @Autowired BootstrapPlatformAdminUseCase bootstrap;
    @Autowired ScopeTestSupport scopes;
    @Autowired OwnerDatabase owner;
    @Autowired FakePaymentProvider stripe;
    @Autowired PricingTestWorld pricing;
    @Autowired ChargeWallet charges;
    @Autowired OutboxRelay relay;
    @Autowired CloseBillingCycleUseCase closeCycle;
    @Value("${billing.tenancy.base-domain}") String baseDomain;

    private final JsonMapper json = JsonMapper.builder().build();
    HttpWorld world;
    Account acme;
    String systemKey;
    String otherSystemKey;
    String viewerToken;
    String customerId;
    String walletId;
    TenantId tenant;

    @BeforeAll
    void createWorld() {
        world = new HttpWorld(mvc, bootstrap, scopes, baseDomain);
        acme = world.newTenant();
        tenant = new TenantId(UUID.fromString(acme.tenantId()));
        systemKey = credential("Sample", List.of(SCOPES_TEXT.split(",")));
        otherSystemKey = credential("Other", List.of(SCOPES_TEXT.split(",")));
        viewerToken = world.newUserToken(acme, java.util.Set.of(world.createRole(acme, "Viewer", java.util.Set.of("invoice:read", "subscription:read"))));
        customerId = world.body(call(HttpMethod.POST, systemKey, "/api/v1/customers", "cust-" + UUID.randomUUID(),
                HttpWorld.Map2.of("externalUserId", "u-" + UUID.randomUUID(), "displayName", "Payer"))).get("id").asString();
        walletId = world.body(call(HttpMethod.POST, systemKey, "/api/v1/wallets", null,
                HttpWorld.Map2.of("customerId", customerId, "currency", "USD", "mode", "POSTPAID", "creditLimit", 1_000_000))).get("id").asString();
        world.body(call(HttpMethod.PUT, acme.adminToken(), "/api/v1/stripe/configuration/direct", null, HttpWorld.Map2.of("secretKey", "sk_test_http_invoicing_" + UUID.randomUUID().toString().replace("-", ""),
                "publishableKey", "pk_test_x", "webhookSecret", "whsec_http_invoicing_secret_0123456789")));
    }

    @BeforeEach
    void resetStripe() {
        stripe.reset();
    }

    private String credential(String name, List<String> scopeList) {
        String systemId = world.body(world.call(HttpMethod.POST, acme.host(), acme.adminToken(), "/api/v1/tenant-systems", HttpWorld.Map2.of("name", name))).get("id").asString();
        return world.body(world.call(HttpMethod.POST, acme.host(), acme.adminToken(), "/api/v1/tenant-systems/" + systemId + "/credentials",
                HttpWorld.Map2.of("label", "x", "scopes", scopeList))).get("key").asString();
    }

    private MvcResult call(HttpMethod method, String token, String path, String idempotencyKey, Object payload) {
        try {
            var builder = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(method, path).header("Host", acme.host());
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

    private int status(MvcResult result) {
        return result.getResponse().getStatus();
    }

    private ChargeResult charge(long amount) {
        return scopes.inTenant(tenant, () -> charges.charge(new ChargeCommand(dev.ngb.core_billing.sharedkernel.WalletId.of(walletId), null, Money.of(amount,
                dev.ngb.core_billing.sharedkernel.Currency.of("USD")), null, "http work", "charge-" + UUID.randomUUID())));
    }

    /** An open invoice for a fresh Postpaid charge, made the way a Tenant System would ask for one. */
    private JsonNode openInvoice(long amount) {
        ChargeResult charged = charge(amount);
        return world.body(call(HttpMethod.POST, systemKey, "/api/v1/invoices", null, HttpWorld.Map2.of("chargeId", charged.id().toString())));
    }

    private String invoiceId(JsonNode detail) {
        return detail.get("invoice").get("id").asString();
    }

    @Test
    void anInvoiceForAChargeIsCreatedOnceAndAskingAgainReturnsIt() {
        ChargeResult charged = charge(1_500);
        MvcResult first = call(HttpMethod.POST, systemKey, "/api/v1/invoices", null, HttpWorld.Map2.of("chargeId", charged.id().toString()));
        MvcResult again = call(HttpMethod.POST, systemKey, "/api/v1/invoices", null, HttpWorld.Map2.of("chargeId", charged.id().toString()));

        assertThat(status(first)).isEqualTo(201);
        assertThat(status(again)).isEqualTo(200);
        JsonNode created = world.body(first);
        assertThat(created.get("invoice").get("status").asString()).isEqualTo("OPEN");
        assertThat(created.get("invoice").get("total").get("amount").asLong()).isEqualTo(1_500);
        assertThat(created.get("invoice").get("number").asString()).startsWith("INV-");
        assertThat(created.get("items")).hasSize(1);
        assertThat(world.body(again).get("invoice").get("id").asString()).isEqualTo(invoiceId(created));
        assertThat(status(call(HttpMethod.POST, systemKey, "/api/v1/invoices", null, HttpWorld.Map2.of("chargeId", UUID.randomUUID().toString())))).isEqualTo(404);
        assertThat(status(call(HttpMethod.POST, systemKey, "/api/v1/invoices", null, HttpWorld.Map2.of()))).isEqualTo(400);
    }

    @Test
    void anAdministratorAndTheMappedSystemSeeInvoicesAndAnotherSystemDoesNot() {
        String id = invoiceId(openInvoice(900));

        for (String token : new String[] {acme.adminToken(), systemKey, viewerToken}) {
            JsonNode list = world.body(call(HttpMethod.GET, token, "/api/v1/invoices?customerId=" + customerId, null, null));
            assertThat(list.valueStream().map(i -> i.get("id").asString())).contains(id);
            assertThat(status(call(HttpMethod.GET, token, "/api/v1/invoices/" + id, null, null))).isEqualTo(200);
        }
        assertThat(world.body(call(HttpMethod.GET, otherSystemKey, "/api/v1/invoices", null, null))).isEmpty();
        assertThat(status(call(HttpMethod.GET, otherSystemKey, "/api/v1/invoices/" + id, null, null))).isEqualTo(404);
        assertThat(status(call(HttpMethod.GET, null, "/api/v1/invoices", null, null))).isEqualTo(401);
    }

    @Test
    void payingStartsAPaymentAndMarksNothingPaid() {
        String id = invoiceId(openInvoice(2_000));

        MvcResult started = call(HttpMethod.POST, systemKey, "/api/v1/invoices/" + id + "/payments", "pay-" + id, null);
        MvcResult replay = call(HttpMethod.POST, systemKey, "/api/v1/invoices/" + id + "/payments", "pay-" + id, null);

        assertThat(status(started)).isEqualTo(201);
        assertThat(status(replay)).isEqualTo(200);
        JsonNode body = world.body(started);
        assertThat(body.get("payment").get("status").asString()).isEqualTo("REQUIRES_ACTION");
        assertThat(body.get("payment").get("checkoutUrl").asString()).isNotBlank();
        assertThat(body.get("invoice").get("invoice").get("status").asString()).isEqualTo("OPEN");
        assertThat(body.get("invoice").get("payments")).hasSize(1);
        assertThat(world.body(replay).get("payment").get("id").asString()).isEqualTo(body.get("payment").get("id").asString());
        assertThat(status(call(HttpMethod.POST, systemKey, "/api/v1/invoices/" + id + "/payments", null, null))).as("an Idempotency-Key is required").isEqualTo(400);
        assertThat(status(call(HttpMethod.POST, otherSystemKey, "/api/v1/invoices/" + id + "/payments", "other-" + id, null))).isEqualTo(404);
        assertThat(status(call(HttpMethod.POST, viewerToken, "/api/v1/invoices/" + id + "/payments", "viewer-" + id, null))).isEqualTo(403);
        JsonNode after = world.body(call(HttpMethod.GET, systemKey, "/api/v1/invoices/" + id, null, null));
        assertThat(after.get("invoice").get("status").asString()).as("only a verified Stripe result can pay it").isEqualTo("OPEN");
    }

    @Test
    void onlyAnAdministratorCanVoidAndItNeedsAReason() {
        String id = invoiceId(openInvoice(700));
        String path = "/api/v1/invoices/" + id + "/void";

        assertThat(status(call(HttpMethod.POST, systemKey, path, null, HttpWorld.Map2.of("reason", "no")))).isEqualTo(403);
        assertThat(status(call(HttpMethod.POST, viewerToken, path, null, HttpWorld.Map2.of("reason", "no")))).isEqualTo(403);
        assertThat(status(call(HttpMethod.POST, acme.adminToken(), path, null, HttpWorld.Map2.of("reason", " ")))).isEqualTo(400);

        JsonNode voided = world.body(call(HttpMethod.POST, acme.adminToken(), path, null, HttpWorld.Map2.of("reason", "issued in error")));
        assertThat(voided.get("invoice").get("status").asString()).isEqualTo("VOID");
        assertThat(voided.get("invoice").get("voidReason").asString()).isEqualTo("issued in error");
        assertThat(status(call(HttpMethod.POST, acme.adminToken(), path, null, HttpWorld.Map2.of("reason", "again")))).isEqualTo(409);
        assertThat(status(call(HttpMethod.POST, systemKey, "/api/v1/invoices/" + id + "/payments", "late-" + id, null))).as("a void invoice cannot be paid").isEqualTo(409);
    }

    @Test
    void anOpenInvoiceCanBeWrittenOffAndStillBePaidLater() {
        String id = invoiceId(openInvoice(700));
        JsonNode written = world.body(call(HttpMethod.POST, acme.adminToken(), "/api/v1/invoices/" + id + "/uncollectible", null, null));
        assertThat(written.get("invoice").get("status").asString()).isEqualTo("UNCOLLECTIBLE");
        assertThat(status(call(HttpMethod.POST, systemKey, "/api/v1/invoices/" + id + "/payments", "late-" + id, null))).isEqualTo(201);
        assertThat(status(call(HttpMethod.POST, systemKey, "/api/v1/invoices/" + id + "/uncollectible", null, null))).isEqualTo(403);
    }

    @Test
    void aSystemCannotBeGrantedTheAdministrativeInvoicePermission() {
        String systemId = world.body(world.call(HttpMethod.POST, acme.host(), acme.adminToken(), "/api/v1/tenant-systems", HttpWorld.Map2.of("name", "Greedy"))).get("id").asString();
        int rejected = world.call(HttpMethod.POST, acme.host(), acme.adminToken(), "/api/v1/tenant-systems/" + systemId + "/credentials",
                HttpWorld.Map2.of("label", "x", "scopes", List.of("invoice:manage"))).getResponse().getStatus();
        assertThat(rejected).isBetween(400, 422);
    }

    @Test
    void aSubscriptionIsCreatedIdempotentlyChargedAtOnceAndCanceledAtPeriodEnd() {
        String planId = pricing.plan(tenant, "pro", pricing.pro(), BillingCycle.MONTHLY, null, null).id().toString();
        java.util.Map<String, Object> request = HttpWorld.Map2.of("walletId", walletId, "planId", planId);
        String key = "sub-" + UUID.randomUUID();

        MvcResult created = call(HttpMethod.POST, systemKey, "/api/v1/subscriptions", key, request);
        MvcResult replay = call(HttpMethod.POST, systemKey, "/api/v1/subscriptions", key, request);

        assertThat(status(created)).isEqualTo(201);
        assertThat(status(replay)).isEqualTo(200);
        JsonNode body = world.body(created);
        String id = body.get("subscription").get("id").asString();
        assertThat(body.get("subscription").get("status").asString()).isEqualTo("ACTIVE");
        assertThat(body.get("subscription").get("periodsCharged").asInt()).isEqualTo(1);
        assertThat(body.get("subscription").get("cycle").asString()).isEqualTo("MONTHLY");
        assertThat(body.get("history").valueStream().map(e -> e.get("type").asString())).containsExactly("CREATED", "RENEWED");
        assertThat(world.body(replay).get("subscription").get("id").asString()).isEqualTo(id);
        assertThat(status(call(HttpMethod.POST, systemKey, "/api/v1/subscriptions", null, request))).as("an Idempotency-Key is required").isEqualTo(400);
        assertThat(status(call(HttpMethod.POST, systemKey, "/api/v1/subscriptions", "bad-" + key, HttpWorld.Map2.of("walletId", walletId)))).isEqualTo(400);

        assertThat(world.body(call(HttpMethod.GET, acme.adminToken(), "/api/v1/subscriptions?customerId=" + customerId, null, null)).valueStream()
                .map(s -> s.get("id").asString())).contains(id);
        assertThat(world.body(call(HttpMethod.GET, otherSystemKey, "/api/v1/subscriptions", null, null))).isEmpty();
        assertThat(status(call(HttpMethod.GET, otherSystemKey, "/api/v1/subscriptions/" + id, null, null))).isEqualTo(404);
        assertThat(status(call(HttpMethod.POST, otherSystemKey, "/api/v1/subscriptions/" + id + "/cancel", null, null))).isEqualTo(404);
        assertThat(status(call(HttpMethod.POST, viewerToken, "/api/v1/subscriptions/" + id + "/cancel", null, null))).isEqualTo(403);

        JsonNode canceled = world.body(call(HttpMethod.POST, systemKey, "/api/v1/subscriptions/" + id + "/cancel", null, null));
        assertThat(canceled.get("subscription").get("cancelAtPeriodEnd").asBoolean()).isTrue();
        assertThat(canceled.get("subscription").get("status").asString()).isEqualTo("ACTIVE");
        JsonNode again = world.body(call(HttpMethod.POST, systemKey, "/api/v1/subscriptions/" + id + "/cancel", null, null));
        assertThat(again.get("history").valueStream().filter(e -> e.get("type").asString().equals("CANCEL_REQUESTED"))).hasSize(1);
    }

    @Test
    void theCycleInvoiceAppearsThroughTheApiWithItsSubscriptionLine() {
        String planId = pricing.plan(tenant, "pro", pricing.pro(), BillingCycle.MONTHLY, null, null).id().toString();
        JsonNode sub = world.body(call(HttpMethod.POST, systemKey, "/api/v1/subscriptions", "sub-" + UUID.randomUUID(), HttpWorld.Map2.of("walletId", walletId, "planId", planId)));
        scopes.runInTenant(tenant, relay::relayDue);
        owner.jdbc().update("update invoice_items set cycle_start = cycle_start - interval '1 month', cycle_end = cycle_end - interval '1 month' "
                + "where wallet_id = ? and invoice_id is null", UUID.fromString(walletId));
        scopes.inTenant(tenant, closeCycle::runOnce);

        JsonNode invoices = world.body(call(HttpMethod.GET, systemKey, "/api/v1/invoices?walletId=" + walletId + "&status=OPEN", null, null));
        JsonNode cycle = invoices.valueStream().filter(i -> i.get("origin").asString().equals("POSTPAID_CYCLE")).findFirst().orElseThrow();
        JsonNode detail = world.body(call(HttpMethod.GET, systemKey, "/api/v1/invoices/" + cycle.get("id").asString(), null, null));
        assertThat(detail.get("items").valueStream().map(i -> i.get("subscriptionId") == null || i.get("subscriptionId").isNull() ? "" : i.get("subscriptionId").asString()))
                .contains(sub.get("subscription").get("id").asString());
        assertThat(detail.get("items").valueStream().map(i -> i.get("source").asString())).contains("SUBSCRIPTION_PERIOD");
    }
}
