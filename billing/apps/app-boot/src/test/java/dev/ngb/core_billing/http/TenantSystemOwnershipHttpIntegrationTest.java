package dev.ngb.core_billing.http;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core_billing.Application;
import dev.ngb.core_billing.boot.infrastructure.outbox.OutboxRelay;
import dev.ngb.core_billing.money.api.wallet.CreditCommand;
import dev.ngb.core_billing.money.api.wallet.CreditWallet;
import dev.ngb.core_billing.platform.application.user.command.BootstrapPlatformAdminUseCase;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.TenantId;
import dev.ngb.core_billing.sharedkernel.WalletId;
import dev.ngb.core_billing.support.FakePaymentProviderConfig;
import dev.ngb.core_billing.support.HttpWorld;
import dev.ngb.core_billing.support.HttpWorld.Account;
import dev.ngb.core_billing.support.PostgresContainerConfig;
import dev.ngb.core_billing.support.RealKeycloakConfig;
import dev.ngb.core_billing.support.ScopeTestSupport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Two Tenant Systems in one Tenant may not read or change each other's resources. Every ID-addressed endpoint is
 * checked from both sides, while a Tenant User keeps the tenant-wide view.
 */
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, RealKeycloakConfig.class, FakePaymentProviderConfig.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenantSystemOwnershipHttpIntegrationTest {

    private static final String STRIPE_SECRET = "sk_test_ownership_9f3a7c21";
    private static final String STRIPE_WEBHOOK_SECRET = "whsec_ownership_secret_0123456789";
    private static final String FROM = "2026-10-01T00:00:00Z";
    private static final String TO = "2026-10-02T00:00:00Z";

    @Autowired MockMvc mvc;
    @Autowired BootstrapPlatformAdminUseCase bootstrap;
    @Autowired ScopeTestSupport scopes;
    @Autowired CreditWallet creditWallet;
    @Autowired OutboxRelay relay;
    @Value("${billing.tenancy.base-domain}") String baseDomain;

    private final JsonMapper json = JsonMapper.builder().build();
    HttpWorld world;
    Account acme;
    String systemA;
    String systemB;
    String keyA;
    String keyB;
    String metric;
    String planId;

    @BeforeAll
    void createWorld() {
        world = new HttpWorld(mvc, bootstrap, scopes, baseDomain);
        acme = world.newTenant();
        systemA = newSystem("A");
        systemB = newSystem("B");
        keyA = newKey(systemA);
        keyB = newKey(systemB);
        connectStripe();
        metric = "m" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        planId = world.body(world.call(HttpMethod.POST, acme.host(), acme.adminToken(), "/api/v1/pricing-plans", HttpWorld.Map2.of(
                "code", "p-" + UUID.randomUUID().toString().substring(0, 8), "name", "Ownership", "currency", "USD", "model", "EVENT_BASED",
                "version", HttpWorld.Map2.of("rule", HttpWorld.Map2.of("metric", metric, "unit", "call", "unitPrice", "1"),
                        "validFrom", "2020-01-01T00:00:00Z")))).get("id").asString();
    }

    private String newSystem(String name) {
        return world.body(world.call(HttpMethod.POST, acme.host(), acme.adminToken(), "/api/v1/tenant-systems",
                HttpWorld.Map2.of("name", "System " + name + " " + UUID.randomUUID()))).get("id").asString();
    }

    private String newKey(String systemId) {
        List<String> scopes = List.of("customer:read", "customer:write", "wallet:read", "wallet:manage", "reservation:manage", "charge:create",
                "usage:record", "usage:read", "pricing:read", "payment:read", "payment:create", "webhook:read", "webhook:manage");
        return world.body(world.call(HttpMethod.POST, acme.host(), acme.adminToken(), "/api/v1/tenant-systems/" + systemId + "/credentials",
                HttpWorld.Map2.of("label", "key", "scopes", scopes))).get("key").asString();
    }

    private void connectStripe() {
        world.body(world.call(HttpMethod.PUT, acme.host(), acme.adminToken(), "/api/v1/stripe/configuration/direct",
                HttpWorld.Map2.of("secretKey", STRIPE_SECRET, "publishableKey", "pk_test_ownership", "webhookSecret", STRIPE_WEBHOOK_SECRET)));
    }

    private String customer(String key, String name) {
        return parse(post(key, "/api/v1/customers",
                HttpWorld.Map2.of("externalUserId", "u-" + UUID.randomUUID(), "displayName", name))).get("id").asString();
    }

    private String externalUserId(String customerId, String key) {
        JsonNode customer = world.body(world.call(HttpMethod.GET, acme.host(), key, "/api/v1/customers/" + customerId, null));
        return customer.get("identities").get(0).get("externalUserId").asString();
    }

    private String wallet(String key, String customerId, long credit) {
        String id = world.body(world.call(HttpMethod.POST, acme.host(), key, "/api/v1/wallets",
                HttpWorld.Map2.of("customerId", customerId, "currency", "USD", "mode", "PREPAID"))).get("id").asString();
        if (credit > 0) {
            scopes.runInTenant(TenantId.of(acme.tenantId()), () -> creditWallet.credit(new CreditCommand(
                    WalletId.of(id), Money.of(credit, Currency.of("USD")), "topup:" + UUID.randomUUID(), null, "credit-" + UUID.randomUUID(),
                    "system/test")));
        }
        return id;
    }

    private String reservation(String key, String walletId, long amount) {
        return parse(post(key, "/api/v1/reservations",
                HttpWorld.Map2.of("walletId", walletId, "amount", amount, "currency", "USD", "ttlSeconds", 600, "reference", "own-" + UUID.randomUUID())))
                .get("id").asString();
    }

    private JsonNode usage(String key, String customerId, String idempotencyKey) {
        MvcResult result = world.call(HttpMethod.POST, acme.host(), key, "/api/v1/usage-events", HttpWorld.Map2.of(
                "customerId", customerId, "metric", metric, "unit", "call", "quantity", "1", "occurredAt", "2026-10-01T10:00:00Z",
                "idempotencyKey", idempotencyKey, "resourceRef", "r-" + idempotencyKey, "sourceEventId", "s-" + idempotencyKey));
        return parse(result);
    }

    private JsonNode parse(MvcResult result) {
        try {
            return json.readTree(result.getResponse().getContentAsString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private int status(HttpMethod method, String token, String path, Object body) {
        return world.status(method, acme.host(), token, path, body);
    }

    /** Every POST in this test that needs one carries a fresh Idempotency-Key. */
    private MvcResult post(String token, String path, Object body) {
        try {
            var builder = MockMvcRequestBuilders.post(path).header("Host", acme.host())
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
            }
            return mvc.perform(builder).andReturn();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private int postStatus(String token, String path, Object body) {
        return post(token, path, body).getResponse().getStatus();
    }

    @Test
    void customersAreScopedToTheirOwnSystem() {
        String a = customer(keyA, "A customer");
        String b = customer(keyB, "B customer");
        String aExternal = externalUserId(a, keyA);

        assertThat(status(HttpMethod.GET, keyB, "/api/v1/customers/" + a, null)).isEqualTo(404);
        assertThat(status(HttpMethod.PUT, keyB, "/api/v1/customers/" + a, HttpWorld.Map2.of("displayName", "stolen"))).isEqualTo(404);
        assertThat(status(HttpMethod.POST, keyB, "/api/v1/customers/" + a + "/deactivate", null)).isEqualTo(404);
        assertThat(status(HttpMethod.GET, keyB, "/api/v1/customers/by-external-user/" + aExternal, null)).isEqualTo(404);

        JsonNode bList = world.body(world.call(HttpMethod.GET, acme.host(), keyB, "/api/v1/customers", null));
        assertThat(bList.toString()).contains(b).doesNotContain(a);
        JsonNode aList = world.body(world.call(HttpMethod.GET, acme.host(), keyA, "/api/v1/customers", null));
        assertThat(aList.toString()).contains(a).doesNotContain(b);

        JsonNode own = world.body(world.call(HttpMethod.GET, acme.host(), keyA, "/api/v1/customers/" + a, null));
        assertThat(own.get("identities")).hasSize(1);
        assertThat(own.get("identities").get(0).get("tenantSystemId").asString()).isEqualTo(systemA);

        assertThat(status(HttpMethod.GET, acme.adminToken(), "/api/v1/customers/" + a, null)).as("a Tenant User sees the whole tenant").isEqualTo(200);
        assertThat(world.body(world.call(HttpMethod.GET, acme.host(), acme.adminToken(), "/api/v1/customers", null)).toString()).contains(a).contains(b);
    }

    @Test
    void walletsReservationsAndChargesAreScopedToTheirCustomer() {
        String a = customer(keyA, "Money A");
        String b = customer(keyB, "Money B");
        String walletA = wallet(keyA, a, 10_000);
        String walletB = wallet(keyB, b, 10_000);

        assertThat(status(HttpMethod.GET, keyB, "/api/v1/wallets/" + walletA, null)).isEqualTo(404);
        assertThat(status(HttpMethod.GET, keyB, "/api/v1/wallets/" + walletA + "/ledger", null)).isEqualTo(404);
        assertThat(status(HttpMethod.GET, keyB, "/api/v1/wallets?customerId=" + a, null)).isEqualTo(404);
        assertThat(status(HttpMethod.POST, keyB, "/api/v1/wallets",
                HttpWorld.Map2.of("customerId", a, "currency", "EUR", "mode", "PREPAID"))).isEqualTo(404);

        assertThat(postStatus(keyB, "/api/v1/reservations",
                HttpWorld.Map2.of("walletId", walletA, "amount", 10, "currency", "USD", "reference", "cross"))).isEqualTo(404);
        assertThat(postStatus(keyB, "/api/v1/charges",
                HttpWorld.Map2.of("walletId", walletA, "amount", 10, "currency", "USD", "reference", "cross"))).isEqualTo(404);

        String reservationA = reservation(keyA, walletA, 100);
        assertThat(status(HttpMethod.GET, keyB, "/api/v1/reservations/" + reservationA, null)).isEqualTo(404);
        assertThat(status(HttpMethod.POST, keyB, "/api/v1/reservations/" + reservationA + "/release", null)).isEqualTo(404);
        assertThat(postStatus(keyB, "/api/v1/reservations/" + reservationA + "/capture",
                HttpWorld.Map2.of("amount", 50, "currency", "USD"))).isEqualTo(404);

        MvcResult captured = post(keyA, "/api/v1/reservations/" + reservationA + "/capture",
                HttpWorld.Map2.of("amount", 50, "currency", "USD"));
        assertThat(captured.getResponse().getStatus()).isEqualTo(201);
        String chargeA = parse(captured).get("id").asString();
        assertThat(status(HttpMethod.GET, keyB, "/api/v1/charges/" + chargeA, null)).isEqualTo(404);
        assertThat(status(HttpMethod.GET, keyA, "/api/v1/charges/" + chargeA, null)).isEqualTo(200);

        assertThat(status(HttpMethod.GET, keyB, "/api/v1/wallets/" + walletB, null)).as("its own wallet still works").isEqualTo(200);
        assertThat(status(HttpMethod.GET, keyA, "/api/v1/wallets/" + walletA, null)).isEqualTo(200);
    }

    @Test
    void usageAndRatingsAreScopedToTheirCustomer() {
        String a = customer(keyA, "Usage A");
        String b = customer(keyB, "Usage B");
        String walletA = wallet(keyA, a, 10_000);
        String walletB = wallet(keyB, b, 10_000);

        JsonNode recordedA = usage(keyA, a, "ua-" + UUID.randomUUID());
        assertThat(recordedA.get("event").get("id").asString()).isNotBlank();
        usage(keyB, b, "ub-" + UUID.randomUUID());

        assertThat(status(HttpMethod.GET, keyB, "/api/v1/usage-events?customerId=" + a, null)).isEqualTo(404);
        assertThat(status(HttpMethod.GET, keyB, "/api/v1/usage-aggregates?customerId=" + a + "&from=" + FROM + "&to=" + TO, null)).isEqualTo(404);
        assertThat(status(HttpMethod.POST, keyB, "/api/v1/usage-events", HttpWorld.Map2.of(
                "customerId", a, "metric", metric, "unit", "call", "quantity", "1", "occurredAt", "2026-10-01T10:00:00Z",
                "idempotencyKey", "cross-" + UUID.randomUUID()))).isEqualTo(404);

        assertThat(postStatus(keyB, "/api/v1/pricing/usage-charges", HttpWorld.Map2.of(
                "customerId", a, "walletId", walletA, "planId", planId, "from", FROM, "to", TO))).isEqualTo(404);
        assertThat(postStatus(keyB, "/api/v1/pricing/reservations", HttpWorld.Map2.of(
                "walletId", walletA, "planId", planId, "usage", List.of(HttpWorld.Map2.of("metric", metric, "quantity", "1")),
                "ttlSeconds", 600, "reference", "cross"))).isEqualTo(404);

        MvcResult charged = post(keyA, "/api/v1/pricing/usage-charges", HttpWorld.Map2.of(
                "customerId", a, "walletId", walletA, "planId", planId, "from", FROM, "to", TO));
        assertThat(charged.getResponse().getStatus()).isEqualTo(201);
        String ratingId = parse(charged).get("id").asString();
        assertThat(status(HttpMethod.GET, keyB, "/api/v1/pricing/usage-charges/" + ratingId, null)).isEqualTo(404);
        assertThat(status(HttpMethod.GET, keyA, "/api/v1/pricing/usage-charges/" + ratingId, null)).isEqualTo(200);

        assertThat(postStatus(keyB, "/api/v1/pricing/usage-charges", HttpWorld.Map2.of(
                "customerId", b, "walletId", walletB, "planId", planId, "from", FROM, "to", TO))).as("its own path works").isNotEqualTo(404);
    }

    @Test
    void paymentsAreScopedToTheWalletOwner() {
        String a = customer(keyA, "Pay A");
        String b = customer(keyB, "Pay B");
        String walletA = wallet(keyA, a, 0);
        String walletB = wallet(keyB, b, 0);

        MvcResult topUpA = post(keyA, "/api/v1/payments/top-ups", HttpWorld.Map2.of(
                "walletId", walletA, "amount", 1000, "currency", "USD"));
        assertThat(topUpA.getResponse().getStatus()).isEqualTo(201);
        String paymentA = parse(topUpA).get("id").asString();

        assertThat(status(HttpMethod.GET, keyB, "/api/v1/payments/" + paymentA, null)).isEqualTo(404);
        assertThat(world.body(world.call(HttpMethod.GET, acme.host(), keyB, "/api/v1/payments", null))).isEmpty();
        assertThat(postStatus(keyB, "/api/v1/payments/top-ups", HttpWorld.Map2.of(
                "walletId", walletA, "amount", 100, "currency", "USD"))).isEqualTo(404);

        assertThat(status(HttpMethod.GET, keyA, "/api/v1/payments/" + paymentA, null)).isEqualTo(200);
        assertThat(world.body(world.call(HttpMethod.GET, acme.host(), keyA, "/api/v1/payments", null))).hasSize(1);
        assertThat(postStatus(keyA, "/api/v1/payments/top-ups", HttpWorld.Map2.of(
                "walletId", walletB, "amount", 100, "currency", "USD"))).isEqualTo(404);
        assertThat(world.body(world.call(HttpMethod.GET, acme.host(), acme.adminToken(), "/api/v1/payments", null))).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void webhookEndpointsAndDeliveriesBelongToTheirSystem() {
        String endpointA = world.body(world.call(HttpMethod.POST, acme.host(), keyA, "/api/v1/webhook-endpoints",
                HttpWorld.Map2.of("url", "https://hooks.example.com/own-" + UUID.randomUUID(), "eventTypes", List.of("customer.created"))))
                .get("endpoint").get("id").asString();

        assertThat(world.body(world.call(HttpMethod.GET, acme.host(), keyB, "/api/v1/webhook-endpoints", null))).isEmpty();
        assertThat(status(HttpMethod.GET, keyB, "/api/v1/webhook-endpoints/" + endpointA, null)).isEqualTo(404);
        assertThat(status(HttpMethod.PATCH, keyB, "/api/v1/webhook-endpoints/" + endpointA, HttpWorld.Map2.of("status", "DISABLED"))).isEqualTo(404);
        assertThat(status(HttpMethod.POST, keyB, "/api/v1/webhook-endpoints/" + endpointA + "/rotate-secret", null)).isEqualTo(404);
        assertThat(world.body(world.call(HttpMethod.GET, acme.host(), keyA, "/api/v1/webhook-endpoints", null)).toString()).contains(endpointA);
        assertThat(world.body(world.call(HttpMethod.GET, acme.host(), acme.adminToken(), "/api/v1/webhook-endpoints", null)).toString()).contains(endpointA);

        customer(keyA, "Delivered A");
        scopes.runInTenant(TenantId.of(acme.tenantId()), relay::relayDue);
        JsonNode deliveriesA = world.body(world.call(HttpMethod.GET, acme.host(), keyA, "/api/v1/webhook-deliveries", null));
        assertThat(deliveriesA).isNotEmpty();
        String deliveryA = deliveriesA.get(0).get("id").asString();
        assertThat(world.body(world.call(HttpMethod.GET, acme.host(), keyB, "/api/v1/webhook-deliveries", null))).isEmpty();
        assertThat(status(HttpMethod.GET, keyB, "/api/v1/webhook-deliveries/" + deliveryA, null)).isEqualTo(404);
        assertThat(status(HttpMethod.POST, keyB, "/api/v1/webhook-deliveries/" + deliveryA + "/resend", null)).isEqualTo(404);
        assertThat(status(HttpMethod.GET, keyA, "/api/v1/webhook-deliveries/" + deliveryA, null)).isEqualTo(200);
    }

    @Test
    void aTenantSystemCannotCreateForAnotherSystem() {
        assertThat(postStatus(keyB, "/api/v1/customers", HttpWorld.Map2.of(
                "tenantSystemId", systemA, "externalUserId", "u-" + UUID.randomUUID(), "displayName", "Impersonation"))).isEqualTo(403);
    }
}
