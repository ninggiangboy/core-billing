package dev.ngb.core_billing.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core.id.TenantId;
import dev.ngb.core_billing.Application;
import dev.ngb.core_billing.money.api.wallet.CreditCommand;
import dev.ngb.core_billing.money.api.wallet.CreditWallet;
import dev.ngb.core_billing.platform.application.user.command.BootstrapPlatformAdminUseCase;
import dev.ngb.core_billing.sharedkernel.id.WalletId;
import dev.ngb.core_billing.sharedkernel.money.Currency;
import dev.ngb.core_billing.sharedkernel.money.Money;
import dev.ngb.core_billing.support.HttpWorld;
import dev.ngb.core_billing.support.HttpWorld.Account;
import dev.ngb.core_billing.support.HttpWorld.Map2;
import dev.ngb.core_billing.support.PostgresContainerConfig;
import dev.ngb.core_billing.support.RealKeycloakConfig;
import dev.ngb.core_billing.support.ScopeTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

/** Pricing plans, estimation, usage ingestion and charging over HTTP: permissions, status codes and the idempotency contract. */
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, RealKeycloakConfig.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PricingUsageHttpIntegrationTest {

    private static final List<String> OPS = List.of("pricing:read", "usage:record", "usage:read", "customer:write", "customer:read", "wallet:manage",
            "wallet:read", "charge:create", "reservation:manage");
    private static final String VALID_FROM = "2026-09-01T00:00:00Z";
    private static final String FROM = "2026-10-01T00:00:00Z";
    private static final String TO = "2026-10-02T00:00:00Z";

    @Autowired MockMvc mvc;
    @Autowired BootstrapPlatformAdminUseCase bootstrap;
    @Autowired ScopeTestSupport scopes;
    @Autowired CreditWallet creditWallet;
    @Value("${billing.tenancy.base-domain}") String baseDomain;

    private final JsonMapper json = JsonMapper.builder().build();
    HttpWorld world;
    Account acme;
    Account globex;
    String ops;
    String readOnly;

    @BeforeAll
    void createWorld() {
        world = new HttpWorld(mvc, bootstrap, scopes, baseDomain);
        acme = world.newTenant();
        globex = world.newTenant();
        String systemId = world.body(world.call(HttpMethod.POST, acme.host(), acme.adminToken(), "/api/v1/tenant-systems", Map2.of("name", "Sample"))).get("id").asString();
        ops = credential(systemId, OPS);
        readOnly = credential(systemId, List.of("pricing:read"));
    }

    private String credential(String systemId, List<String> scopes) {
        return world.body(world.call(HttpMethod.POST, acme.host(), acme.adminToken(), "/api/v1/tenant-systems/" + systemId + "/credentials",
                Map2.of("label", "x", "scopes", scopes))).get("key").asString();
    }

    private String metric() {
        return "m" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String code() {
        return "p-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private MvcResult call(Account account, String token, HttpMethod method, String path, Object body) {
        return world.call(method, account.host(), token, path, body);
    }

    private JsonNode ok(MvcResult result) {
        return world.body(result);
    }

    private MvcResult withHeader(String token, String path, String idempotencyKey, Object body) {
        try {
            var request = MockMvcRequestBuilders.post(path).header("Host", acme.host()).header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
            if (idempotencyKey != null) {
                request.header("Idempotency-Key", idempotencyKey);
            }
            return mvc.perform(request).andReturn();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode parse(MvcResult result) {
        try {
            return json.readTree(result.getResponse().getContentAsString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> plan(String code, String model, Map<String, Object> rule) {
        return Map2.of("code", code, "name", "Plan " + code, "currency", "USD", "model", model,
                "version", Map2.of("rule", rule, "validFrom", VALID_FROM, "rounding", "HALF_UP"));
    }

    private String createPlan(String model, Map<String, Object> rule) {
        return ok(call(acme, acme.adminToken(), HttpMethod.POST, "/api/v1/pricing-plans", plan(code(), model, rule))).get("id").asString();
    }

    private Map<String, Object> storageRule(String metric) {
        return Map2.of("metric", metric, "unit", "gb", "mode", "GRADUATED",
                "tiers", List.of(Map2.of("upTo", "100", "unitPrice", "0"), Map2.of("upTo", "1000", "unitPrice", "2"), Map2.of("unitPrice", "1.5")));
    }

    private String customer() {
        return ok(withHeader(ops, "/api/v1/customers", "cust-" + UUID.randomUUID(), Map2.of("externalUserId", "u-" + UUID.randomUUID(), "displayName", "Co"))).get("id").asString();
    }

    private String wallet(String customerId, long balance) {
        String id = ok(call(acme, ops, HttpMethod.POST, "/api/v1/wallets", Map2.of("customerId", customerId, "currency", "USD", "mode", "PREPAID"))).get("id").asString();
        if (balance > 0) {
            scopes.runInTenant(TenantId.of(acme.tenantId()), () -> creditWallet.credit(new CreditCommand(WalletId.of(id), Money.of(balance, Currency.of("USD")),
                    "topup:" + UUID.randomUUID(), null, "credit-" + UUID.randomUUID(), "system/test")));
        }
        return id;
    }

    private Map<String, Object> usageEvent(String customerId, String metric, String unit, String quantity, String key) {
        return Map2.of("customerId", customerId, "metric", metric, "unit", unit, "quantity", quantity, "occurredAt", "2026-10-01T10:00:00Z", "idempotencyKey", key,
                "resourceRef", "res-1", "sourceEventId", "src-" + key);
    }

    // ---- plans

    @Test
    void everyPricingModelCanBeCreatedAndTenantSystemsListTheActivePlans() {
        String m1 = metric();
        String m2 = metric();
        String m3 = metric();
        String m4 = metric();
        String m5 = metric();
        String time = createPlan("TIME_BASED", Map2.of("metric", m1, "unit", "hour", "unitPrice", "3.5", "increment", "0.25"));
        String usage = createPlan("USAGE_BASED", storageRule(m2));
        String workload = createPlan("WORKLOAD_BASED", Map2.of("metric", m3, "unit", "cpu-second", "unitPrice", "0.1", "executionFee", "5"));
        String event = createPlan("EVENT_BASED", Map2.of("metric", m4, "unit", "call", "unitPrice", "0.0000004"));
        String result = ok(call(acme, acme.adminToken(), HttpMethod.POST, "/api/v1/pricing-plans", Map2.of("code", code(), "name", "Pro", "currency", "USD", "model", "SUBSCRIPTION",
                "version", Map2.of("rule", Map2.of("fixedPrice", "2900"), "billingCycle", "MONTHLY")))).get("id").asString();
        String hybrid = ok(call(acme, acme.adminToken(), HttpMethod.POST, "/api/v1/pricing-plans", Map2.of("code", code(), "name", "Package", "currency", "USD", "model", "HYBRID",
                "version", Map2.of("billingCycle", "MONTHLY", "rule", Map2.of("fixedPrice", "2900", "components", List.of(
                        Map2.of("model", "EVENT_BASED", "metric", m5, "unit", "request", "unitPrice", "0.01"),
                        Map2.of("model", "USAGE_BASED", "metric", metric(), "unit", "gb", "unitPrice", "2"))))))).get("id").asString();

        JsonNode listed = ok(call(acme, ops, HttpMethod.GET, "/api/v1/pricing-plans?status=ACTIVE", null));
        List<String> ids = listed.valueStream().map(n -> n.get("id").asString()).toList();
        assertThat(ids).contains(time, usage, workload, event, result, hybrid);
        JsonNode eventPlan = ok(call(acme, ops, HttpMethod.GET, "/api/v1/pricing-plans/" + event, null));
        assertThat(eventPlan.get("versions").get(0).get("rule").get("unitPrice").asString()).as("a fraction of a cent survives").isEqualTo("0.0000004");
        assertThat(ok(call(acme, ops, HttpMethod.GET, "/api/v1/pricing-plans/" + hybrid, null)).get("versions").get(0).get("rule").get("components")).hasSize(2);
    }

    @Test
    void onlyAdministratorsManagePlans() {
        Map<String, Object> body = plan(code(), "EVENT_BASED", Map2.of("metric", metric(), "unit", "call", "unitPrice", "1"));
        assertThat(call(acme, ops, HttpMethod.POST, "/api/v1/pricing-plans", body).getResponse().getStatus()).as("a tenant system cannot set prices").isEqualTo(403);
        assertThat(call(acme, readOnly, HttpMethod.POST, "/api/v1/pricing-plans", body).getResponse().getStatus()).isEqualTo(403);
        assertThat(call(acme, readOnly, HttpMethod.GET, "/api/v1/pricing-plans", null).getResponse().getStatus()).isEqualTo(200);
        assertThat(call(acme, null, HttpMethod.GET, "/api/v1/pricing-plans", null).getResponse().getStatus()).isEqualTo(401);
        assertThat(call(acme, acme.adminToken(), HttpMethod.POST, "/api/v1/pricing-plans", body).getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void invalidPlansAreRejectedWithTheReason() {
        String c = code();
        Map<String, Object> ok = plan(c, "EVENT_BASED", Map2.of("metric", metric(), "unit", "call", "unitPrice", "1"));
        assertThat(call(acme, acme.adminToken(), HttpMethod.POST, "/api/v1/pricing-plans", ok).getResponse().getStatus()).isEqualTo(201);
        assertThat(call(acme, acme.adminToken(), HttpMethod.POST, "/api/v1/pricing-plans", ok).getResponse().getStatus()).as("duplicate code").isEqualTo(409);

        String[][] bad = {
            {"UNKNOWN_MODEL", "x"}, {"EVENT_BASED", "{\"metric\":\"Bad Metric\",\"unit\":\"call\",\"unitPrice\":\"1\"}"},
            {"EVENT_BASED", "{\"metric\":\"m-ok\",\"unit\":\"call\",\"unitPrice\":\"-1\"}"}, {"EVENT_BASED", "{\"metric\":\"m-ok\",\"unit\":\"call\",\"unitPrice\":\"abc\"}"},
            {"EVENT_BASED", "{\"metric\":\"m-ok\",\"unit\":\"call\"}"},
            {"USAGE_BASED", "{\"metric\":\"m-ok\",\"unit\":\"gb\",\"tiers\":[{\"upTo\":\"10\",\"unitPrice\":\"1\"}]}"},
            {"USAGE_BASED", "{\"metric\":\"m-ok\",\"unit\":\"gb\",\"tiers\":[{\"upTo\":\"10\",\"unitPrice\":\"1\"},{\"upTo\":\"5\",\"unitPrice\":\"1\"},{\"unitPrice\":\"1\"}]}"},
            {"SUBSCRIPTION", "{\"fixedPrice\":\"10\"}"}, {"HYBRID", "{\"fixedPrice\":\"10\"}"}, {"TIME_BASED", "{\"model\":\"EVENT_BASED\",\"metric\":\"m-ok\",\"unit\":\"hour\",\"unitPrice\":\"1\"}"}};
        for (String[] b : bad) {
            Map<String, Object> request = Map2.of("code", code(), "name", "x", "currency", "USD", "model", b[0], "version", Map2.of("rule", rawRule(b[1])));
            assertThat(call(acme, acme.adminToken(), HttpMethod.POST, "/api/v1/pricing-plans", request).getResponse().getStatus()).as(b[0] + " " + b[1]).isEqualTo(400);
        }
        assertThat(call(acme, acme.adminToken(), HttpMethod.POST, "/api/v1/pricing-plans", Map2.of("code", code(), "name", "x", "currency", "USD", "model", "EVENT_BASED"))
                .getResponse().getStatus()).as("no version").isEqualTo(400);
        assertThat(call(acme, acme.adminToken(), HttpMethod.POST, "/api/v1/pricing-plans", Map2.of("code", code(), "name", "x", "currency", "ZZZ", "model", "EVENT_BASED",
                "version", Map2.of("rule", Map2.of("metric", metric(), "unit", "call", "unitPrice", "1")))).getResponse().getStatus()).isEqualTo(400);
    }

    private Object rawRule(String text) {
        if (!text.startsWith("{")) {
            return "not-an-object";
        }
        return json.readValue(text, Map.class);
    }

    @Test
    void aMetricKeepsOneUnitAcrossPlans() {
        String metric = metric();
        createPlan("EVENT_BASED", Map2.of("metric", metric, "unit", "call", "unitPrice", "1"));
        assertThat(call(acme, acme.adminToken(), HttpMethod.POST, "/api/v1/pricing-plans", plan(code(), "EVENT_BASED", Map2.of("metric", metric, "unit", "request", "unitPrice", "1")))
                .getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void versionsAreAddedAndPlansAreRetiredOverHttp() {
        String metric = metric();
        String id = createPlan("EVENT_BASED", Map2.of("metric", metric, "unit", "call", "unitPrice", "1"));
        JsonNode v2 = ok(call(acme, acme.adminToken(), HttpMethod.POST, "/api/v1/pricing-plans/" + id + "/versions",
                Map2.of("rule", Map2.of("metric", metric, "unit", "call", "unitPrice", "2"), "validFrom", "2026-12-01T00:00:00Z", "rounding", "UP")));
        assertThat(v2.get("version").asInt()).isEqualTo(2);
        assertThat(call(acme, ops, HttpMethod.POST, "/api/v1/pricing-plans/" + id + "/versions", Map2.of("rule", Map2.of("metric", metric, "unit", "call", "unitPrice", "3")))
                .getResponse().getStatus()).isEqualTo(403);
        assertThat(call(acme, acme.adminToken(), HttpMethod.POST, "/api/v1/pricing-plans/" + id + "/versions",
                Map2.of("rule", Map2.of("metric", metric, "unit", "call", "unitPrice", "3"), "validFrom", "2026-11-01T00:00:00Z")).getResponse().getStatus())
                .as("a version cannot start before the previous one").isEqualTo(400);

        assertThat(ok(call(acme, acme.adminToken(), HttpMethod.POST, "/api/v1/pricing-plans/" + id + "/retire", null)).get("status").asString()).isEqualTo("RETIRED");
        assertThat(call(acme, acme.adminToken(), HttpMethod.POST, "/api/v1/pricing-plans/" + id + "/retire", null).getResponse().getStatus()).isEqualTo(409);
        assertThat(call(acme, ops, HttpMethod.POST, "/api/v1/pricing/estimates", Map2.of("planId", id, "usage", List.of(Map2.of("metric", metric, "quantity", "1"))))
                .getResponse().getStatus()).as("a retired plan is not offered").isEqualTo(409);
        assertThat(ok(call(acme, ops, HttpMethod.GET, "/api/v1/pricing-plans?status=ACTIVE", null)).valueStream().map(n -> n.get("id").asString())).doesNotContain(id);
    }

    // ---- estimation

    @Test
    void anEstimateIsAPureCalculationWithABreakdown() {
        String metric = metric();
        String id = createPlan("USAGE_BASED", storageRule(metric));
        JsonNode estimate = ok(call(acme, readOnly, HttpMethod.POST, "/api/v1/pricing/estimates",
                Map2.of("planId", id, "usage", List.of(Map2.of("metric", metric, "quantity", "1100")))));

        assertThat(estimate.get("amount").get("amount").asLong()).isEqualTo(1950);
        assertThat(estimate.get("amount").get("currency").asString()).isEqualTo("USD");
        assertThat(estimate.get("planVersion").asInt()).isEqualTo(1);
        assertThat(estimate.get("adjustment").asString()).isEqualTo("NONE");
        assertThat(estimate.get("lines")).hasSize(3);
        assertThat(estimate.get("lines").get(1).get("amount").asString()).isEqualTo("1800");

        assertThat(call(acme, readOnly, HttpMethod.POST, "/api/v1/pricing/estimates", Map2.of("planId", UUID.randomUUID().toString(), "usage", List.of())).getResponse().getStatus())
                .isEqualTo(404);
        assertThat(call(acme, readOnly, HttpMethod.POST, "/api/v1/pricing/estimates", Map2.of("planId", id, "usage", List.of(Map2.of("metric", "other", "quantity", "1"))))
                .getResponse().getStatus()).isEqualTo(400);
        assertThat(call(globex, globex.adminToken(), HttpMethod.POST, "/api/v1/pricing/estimates", Map2.of("planId", id, "usage", List.of())).getResponse().getStatus())
                .as("another tenant's plan").isEqualTo(404);
    }

    // ---- usage

    @Test
    void usageFollowsTheIdempotencyContractAndIsValidated() {
        String metric = metric();
        createPlan("EVENT_BASED", Map2.of("metric", metric, "unit", "call", "unitPrice", "1"));
        String customer = customer();
        String key = "evt-" + UUID.randomUUID();

        MvcResult first = call(acme, ops, HttpMethod.POST, "/api/v1/usage-events", usageEvent(customer, metric, "call", "5", key));
        MvcResult again = call(acme, ops, HttpMethod.POST, "/api/v1/usage-events", usageEvent(customer, metric, "call", "5", key));
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(again.getResponse().getStatus()).isEqualTo(200);
        assertThat(parse(again).get("replayed").asBoolean()).isTrue();
        assertThat(parse(again).get("event").get("id").asString()).isEqualTo(parse(first).get("event").get("id").asString());
        assertThat(parse(first).get("event").get("tenantSystemId").isNull()).as("the credential's system is traced").isFalse();
        assertThat(parse(first).get("event").get("resourceRef").asString()).isEqualTo("res-1");

        assertThat(call(acme, ops, HttpMethod.POST, "/api/v1/usage-events", usageEvent(customer, metric, "call", "6", key)).getResponse().getStatus()).isEqualTo(409);
        assertThat(call(acme, ops, HttpMethod.POST, "/api/v1/usage-events", usageEvent(customer, "no-such-metric", "call", "1", "k1")).getResponse().getStatus()).isEqualTo(400);
        assertThat(call(acme, ops, HttpMethod.POST, "/api/v1/usage-events", usageEvent(customer, metric, "gb", "1", "k2")).getResponse().getStatus()).isEqualTo(400);
        assertThat(call(acme, ops, HttpMethod.POST, "/api/v1/usage-events", usageEvent(customer, metric, "call", "0", "k3")).getResponse().getStatus()).isEqualTo(400);
        assertThat(call(acme, ops, HttpMethod.POST, "/api/v1/usage-events", usageEvent(customer, metric, "call", "abc", "k4")).getResponse().getStatus()).isEqualTo(400);
        assertThat(call(acme, ops, HttpMethod.POST, "/api/v1/usage-events", usageEvent(UUID.randomUUID().toString(), metric, "call", "1", "k5")).getResponse().getStatus()).isEqualTo(404);
        assertThat(call(acme, readOnly, HttpMethod.POST, "/api/v1/usage-events", usageEvent(customer, metric, "call", "1", "k6")).getResponse().getStatus()).isEqualTo(403);
        assertThat(call(globex, globex.adminToken(), HttpMethod.POST, "/api/v1/usage-events", usageEvent(customer, metric, "call", "1", "k7")).getResponse().getStatus())
                .as("another tenant").isEqualTo(400);
    }

    @Test
    void aBatchIsAllOrNothingOverHttp() {
        String metric = metric();
        createPlan("EVENT_BASED", Map2.of("metric", metric, "unit", "call", "unitPrice", "1"));
        String customer = customer();
        String k1 = "b-" + UUID.randomUUID();
        String k2 = "b-" + UUID.randomUUID();

        MvcResult bad = call(acme, ops, HttpMethod.POST, "/api/v1/usage-events/batch", Map2.of("events", List.of(
                usageEvent(customer, metric, "call", "1", k1), usageEvent(customer, metric, "call", "-1", k2))));
        assertThat(bad.getResponse().getStatus()).isEqualTo(400);
        assertThat(ok(call(acme, ops, HttpMethod.GET, "/api/v1/usage-events?customerId=" + customer, null))).isEmpty();

        JsonNode good = ok(call(acme, ops, HttpMethod.POST, "/api/v1/usage-events/batch", Map2.of("events", List.of(
                usageEvent(customer, metric, "call", "1", k1), usageEvent(customer, metric, "call", "2", k2)))));
        assertThat(good.get("results")).hasSize(2);
        JsonNode retry = ok(call(acme, ops, HttpMethod.POST, "/api/v1/usage-events/batch", Map2.of("events", List.of(
                usageEvent(customer, metric, "call", "1", k1), usageEvent(customer, metric, "call", "2", k2)))));
        assertThat(retry.get("results").valueStream().map(n -> n.get("replayed").asBoolean())).containsExactly(true, true);
        assertThat(ok(call(acme, ops, HttpMethod.GET, "/api/v1/usage-events?customerId=" + customer, null))).hasSize(2);
        assertThat(call(acme, ops, HttpMethod.POST, "/api/v1/usage-events/batch", Map2.of("events", List.of())).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void usageHistoryPagesAndAggregates() {
        String metric = metric();
        createPlan("EVENT_BASED", Map2.of("metric", metric, "unit", "call", "unitPrice", "1"));
        String customer = customer();
        for (int i = 0; i < 5; i++) {
            ok(call(acme, ops, HttpMethod.POST, "/api/v1/usage-events", usageEvent(customer, metric, "call", String.valueOf(i + 1), "h-" + UUID.randomUUID())));
        }
        JsonNode first = ok(call(acme, ops, HttpMethod.GET, "/api/v1/usage-events?customerId=" + customer + "&limit=3", null));
        JsonNode second = ok(call(acme, ops, HttpMethod.GET, "/api/v1/usage-events?customerId=" + customer + "&limit=3&before=" + first.get(2).get("id").asString(), null));
        assertThat(first).hasSize(3);
        assertThat(second).hasSize(2);

        JsonNode totals = ok(call(acme, ops, HttpMethod.GET, "/api/v1/usage-aggregates?customerId=" + customer + "&from=" + FROM + "&to=" + TO, null));
        assertThat(totals).hasSize(1);
        assertThat(totals.get(0).get("metric").asString()).isEqualTo(metric);
        assertThat(totals.get(0).get("quantity").asString()).isEqualTo("15");
        assertThat(totals.get(0).get("events").asInt()).isEqualTo(5);
        assertThat(call(acme, ops, HttpMethod.GET, "/api/v1/usage-aggregates?customerId=" + customer + "&from=" + TO + "&to=" + FROM, null).getResponse().getStatus()).isEqualTo(400);
        assertThat(call(acme, readOnly, HttpMethod.GET, "/api/v1/usage-events?customerId=" + customer, null).getResponse().getStatus()).isEqualTo(403);
        assertThat(call(globex, globex.adminToken(), HttpMethod.GET, "/api/v1/usage-events?customerId=" + customer, null).getResponse().getStatus()).isEqualTo(404);
    }

    // ---- charging and reserving

    @Test
    void chargingUsageFollowsTheStatusCodesAndIsIdempotent() {
        String metric = metric();
        String plan = createPlan("USAGE_BASED", storageRule(metric));
        String customer = customer();
        String wallet = wallet(customer, 500);
        ok(call(acme, ops, HttpMethod.POST, "/api/v1/usage-events", usageEvent(customer, metric, "gb", "1100", "u-" + UUID.randomUUID())));
        Map<String, Object> request = Map2.of("customerId", customer, "walletId", wallet, "planId", plan, "from", FROM, "to", TO);

        MvcResult broke = call(acme, ops, HttpMethod.POST, "/api/v1/pricing/usage-charges", request);
        assertThat(broke.getResponse().getStatus()).as("500 does not cover 1950").isEqualTo(402);
        assertThat(parse(broke).get("failureReason").asString()).isEqualTo("insufficient_funds");

        scopes.runInTenant(TenantId.of(acme.tenantId()), () -> creditWallet.credit(new CreditCommand(WalletId.of(wallet), Money.of(5000, Currency.of("USD")),
                "topup:" + UUID.randomUUID(), null, "credit-" + UUID.randomUUID(), "system/test")));
        MvcResult paid = call(acme, ops, HttpMethod.POST, "/api/v1/pricing/usage-charges", request);
        assertThat(paid.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = parse(paid);
        assertThat(body.get("outcome").asString()).isEqualTo("CHARGED");
        assertThat(body.get("amount").get("amount").asLong()).isEqualTo(1950);
        assertThat(body.get("planVersion").asInt()).isEqualTo(1);
        assertThat(body.get("lines")).hasSize(3);

        MvcResult repeated = call(acme, ops, HttpMethod.POST, "/api/v1/pricing/usage-charges", request);
        assertThat(repeated.getResponse().getStatus()).isEqualTo(200);
        assertThat(parse(repeated).get("replayed").asBoolean()).isTrue();
        assertThat(parse(repeated).get("id").asString()).isEqualTo(body.get("id").asString());
        assertThat(ok(call(acme, ops, HttpMethod.GET, "/api/v1/wallets/" + wallet, null)).get("balance").get("amount").asLong()).isEqualTo(500 + 5000 - 1950);
        assertThat(ok(call(acme, readOnly, HttpMethod.GET, "/api/v1/pricing/usage-charges/" + body.get("id").asString(), null)).get("amount").get("amount").asLong()).isEqualTo(1950);

        Map<String, Object> nothing = Map2.of("customerId", customer, "walletId", wallet, "planId", plan, "from", "2026-01-01T00:00:00Z", "to", "2026-01-02T00:00:00Z");
        MvcResult none = call(acme, ops, HttpMethod.POST, "/api/v1/pricing/usage-charges", nothing);
        assertThat(none.getResponse().getStatus()).isEqualTo(409); // the plan has no version in force that early
        assertThat(call(acme, readOnly, HttpMethod.POST, "/api/v1/pricing/usage-charges", request).getResponse().getStatus()).isEqualTo(403);
        assertThat(call(acme, ops, HttpMethod.POST, "/api/v1/pricing/usage-charges", Map2.of("customerId", customer, "walletId", wallet, "planId", plan)).getResponse().getStatus())
                .isEqualTo(400);
        assertThat(call(globex, globex.adminToken(), HttpMethod.POST, "/api/v1/pricing/usage-charges", request).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void aReservationFromAnEstimateHoldsTheEstimatedAmount() {
        String metric = metric();
        String plan = createPlan("TIME_BASED", Map2.of("metric", metric, "unit", "hour", "unitPrice", "3.5"));
        String customer = customer();
        String wallet = wallet(customer, 100);
        Map<String, Object> request = Map2.of("walletId", wallet, "planId", plan, "usage", List.of(Map2.of("metric", metric, "quantity", "4")), "ttlSeconds", 600, "reference", "vm-1");
        String key = "res-" + UUID.randomUUID();

        assertThat(withHeader(ops, "/api/v1/pricing/reservations", null, request).getResponse().getStatus()).as("an Idempotency-Key is required").isEqualTo(400);
        MvcResult created = withHeader(ops, "/api/v1/pricing/reservations", key, request);
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        assertThat(parse(created).get("estimate").get("amount").get("amount").asLong()).isEqualTo(14);
        assertThat(parse(created).get("reservation").get("amount").get("amount").asLong()).isEqualTo(14);
        MvcResult replay = withHeader(ops, "/api/v1/pricing/reservations", key, request);
        assertThat(replay.getResponse().getStatus()).as("a replayed reservation answers 200").isEqualTo(200);
        assertThat(parse(replay).get("reservation").get("id").asString()).isEqualTo(parse(created).get("reservation").get("id").asString());
        assertThat(parse(replay).get("reservation").get("replayed").asBoolean()).isTrue();
        assertThat(ok(call(acme, ops, HttpMethod.GET, "/api/v1/wallets/" + wallet, null)).get("held").get("amount").asLong()).isEqualTo(14);

        Map<String, Object> tooBig = Map2.of("walletId", wallet, "planId", plan, "usage", List.of(Map2.of("metric", metric, "quantity", "1000")));
        assertThat(withHeader(ops, "/api/v1/pricing/reservations", "res-" + UUID.randomUUID(), tooBig).getResponse().getStatus()).isEqualTo(402);
        assertThat(withHeader(readOnly, "/api/v1/pricing/reservations", "res-" + UUID.randomUUID(), request).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void theOccurredAtMustNotBeInTheFuture() {
        String metric = metric();
        createPlan("EVENT_BASED", Map2.of("metric", metric, "unit", "call", "unitPrice", "1"));
        Map<String, Object> event = usageEvent(customer(), metric, "call", "1", "future-" + UUID.randomUUID());
        event.put("occurredAt", Instant.now().plusSeconds(3600).toString());
        assertThat(call(acme, ops, HttpMethod.POST, "/api/v1/usage-events", event).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void missingIdentifiersAreBadRequestsNotServerErrors() {
        assertThat(call(acme, ops, HttpMethod.POST, "/api/v1/pricing/estimates", Map2.of("usage", List.of())).getResponse().getStatus()).isEqualTo(400);
        assertThat(call(acme, ops, HttpMethod.POST, "/api/v1/pricing/usage-charges", Map2.of("from", FROM, "to", TO)).getResponse().getStatus()).isEqualTo(400);
        assertThat(withHeader(ops, "/api/v1/pricing/reservations", "k-" + UUID.randomUUID(), Map2.of("usage", List.of())).getResponse().getStatus()).isEqualTo(400);
        assertThat(call(acme, ops, HttpMethod.POST, "/api/v1/usage-events", Map2.of("metric", "x", "quantity", "1")).getResponse().getStatus()).isEqualTo(400);
        assertThat(call(acme, acme.adminToken(), HttpMethod.POST, "/api/v1/pricing-plans", Map2.of("code", code(), "name", "x", "model", "EVENT_BASED",
                "version", Map2.of("rule", Map2.of("metric", metric(), "unit", "call", "unitPrice", "1")))).getResponse().getStatus()).isEqualTo(400);
        assertThat(call(acme, ops, HttpMethod.GET, "/api/v1/pricing-plans/not-a-uuid", null).getResponse().getStatus()).isEqualTo(400);
    }
}
