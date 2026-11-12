package dev.ngb.billing.money;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.billing.Application;
import dev.ngb.billing.money.api.wallet.CreditCommand;
import dev.ngb.billing.money.api.wallet.CreditWallet;
import dev.ngb.billing.platform.application.user.command.BootstrapPlatformAdminUseCase;
import dev.ngb.billing.sharedkernel.id.WalletId;
import dev.ngb.billing.sharedkernel.money.Money;
import dev.ngb.billing.support.HttpWorld;
import dev.ngb.billing.support.HttpWorld.Account;
import dev.ngb.billing.support.PostgresContainerConfig;
import dev.ngb.billing.support.RealKeycloakConfig;
import dev.ngb.billing.support.ScopeTestSupport;
import java.util.List;
import java.util.Set;
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

/** The money API over HTTP: who may do what, idempotency headers, and the 402 semantics. */
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, RealKeycloakConfig.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MoneyHttpIntegrationTest {

    private static final List<String> MONEY_SCOPES = List.of("customer:read", "customer:write", "wallet:read", "wallet:manage", "reservation:manage", "charge:create");

    @Autowired MockMvc mvc;
    @Autowired BootstrapPlatformAdminUseCase bootstrap;
    @Autowired ScopeTestSupport scopes;
    @Autowired CreditWallet creditWallet;
    @Value("${billing.tenancy.base-domain}") String baseDomain;

    private final JsonMapper json = JsonMapper.builder().build();
    HttpWorld world;
    Account acme;
    Account globex;
    String key;
    String readOnlyKey;
    String customerId;

    @BeforeAll
    void createWorld() {
        world = new HttpWorld(mvc, bootstrap, scopes, baseDomain);
        acme = world.newTenant();
        globex = world.newTenant();
        String systemId = world.body(world.call(HttpMethod.POST, acme.host(), acme.adminToken(), "/api/v1/tenant-systems",
                HttpWorld.Map2.of("name", "Sample"))).get("id").asString();
        key = credential(systemId, MONEY_SCOPES);
        readOnlyKey = credential(systemId, List.of("wallet:read"));
        customerId = world.body(post(acme, key, "cust-" + UUID.randomUUID(), "/api/v1/customers", HttpWorld.Map2.of("externalUserId", "user-" + UUID.randomUUID(), "displayName", "Acme Ltd"))).get("id").asString();
    }

    private String credential(String systemId, List<String> scopes) {
        return world.body(world.call(HttpMethod.POST, acme.host(), acme.adminToken(), "/api/v1/tenant-systems/" + systemId + "/credentials",
                HttpWorld.Map2.of("label", "x", "scopes", scopes))).get("key").asString();
    }

    private MvcResult post(Account account, String token, String idempotencyKey, String path, Object body) {
        try {
            var request = MockMvcRequestBuilders.post(path).header("Host", account.host()).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body));
            if (token != null) {
                request.header("Authorization", "Bearer " + token);
            }
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

    /** Opens a wallet for a customer of its own, so tests never share a balance. */
    private String openWallet(String currency, String mode) {
        String ownCustomer = world.body(post(acme, key, "cust-" + UUID.randomUUID(), "/api/v1/customers",
                HttpWorld.Map2.of("externalUserId", "user-" + UUID.randomUUID(), "displayName", "Customer"))).get("id").asString();
        MvcResult result = world.call(HttpMethod.POST, acme.host(), key, "/api/v1/wallets",
                HttpWorld.Map2.of("customerId", ownCustomer, "currency", currency, "mode", mode));
        return world.body(result).get("id").asString();
    }

    private void credit(String walletId, long amount, String currency) {
        scopes.runInTenant(new dev.ngb.core.id.TenantId(UUID.fromString(acme.tenantId())), () -> creditWallet.credit(new CreditCommand(
                WalletId.of(walletId), Money.of(amount, dev.ngb.billing.sharedkernel.money.Currency.of(currency)), "topup:" + UUID.randomUUID(), null,
                "credit-" + UUID.randomUUID(), "system/test")));
    }

    @Test
    void aWalletIsOpenedOncePerCustomerAndCurrency() {
        MvcResult first = world.call(HttpMethod.POST, acme.host(), key, "/api/v1/wallets",
                HttpWorld.Map2.of("customerId", customerId, "currency", "EUR", "mode", "PREPAID"));
        MvcResult again = world.call(HttpMethod.POST, acme.host(), key, "/api/v1/wallets",
                HttpWorld.Map2.of("customerId", customerId, "currency", "EUR", "mode", "PREPAID"));
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(again.getResponse().getStatus()).isEqualTo(200);
        assertThat(parse(again).get("id").asString()).isEqualTo(parse(first).get("id").asString());

        assertThat(world.status(HttpMethod.POST, acme.host(), key, "/api/v1/wallets",
                HttpWorld.Map2.of("customerId", customerId, "currency", "EUR", "mode", "POSTPAID", "creditLimit", 100))).isEqualTo(409);
        assertThat(world.status(HttpMethod.POST, acme.host(), key, "/api/v1/wallets",
                HttpWorld.Map2.of("customerId", UUID.randomUUID().toString(), "currency", "EUR", "mode", "PREPAID"))).isEqualTo(404);
        assertThat(world.status(HttpMethod.POST, acme.host(), key, "/api/v1/wallets",
                HttpWorld.Map2.of("customerId", customerId, "currency", "ZZZ", "mode", "PREPAID"))).isEqualTo(400);
        assertThat(world.status(HttpMethod.POST, acme.host(), key, "/api/v1/wallets",
                HttpWorld.Map2.of("customerId", customerId, "currency", "GBP", "mode", "PREPAID", "creditLimit", 5))).as("prepaid has no credit limit").isEqualTo(400);
    }

    @Test
    void reserveCaptureAndChargeFollowTheIdempotencyContract() {
        String wallet = openWallet("USD", "PREPAID");
        credit(wallet, 10_000, "USD");

        assertThat(post(acme, key, null, "/api/v1/reservations", HttpWorld.Map2.of("walletId", wallet, "amount", 100, "currency", "USD"))
                .getResponse().getStatus()).as("missing Idempotency-Key").isEqualTo(400);

        MvcResult reserved = post(acme, key, "res-1", "/api/v1/reservations",
                HttpWorld.Map2.of("walletId", wallet, "amount", 4_000, "currency", "USD", "ttlSeconds", 600, "reference", "vm-1"));
        assertThat(reserved.getResponse().getStatus()).isEqualTo(201);
        String reservationId = parse(reserved).get("id").asString();
        MvcResult reservationReplay = post(acme, key, "res-1", "/api/v1/reservations",
                HttpWorld.Map2.of("walletId", wallet, "amount", 4_000, "currency", "USD", "ttlSeconds", 600, "reference", "vm-1"));
        assertThat(reservationReplay.getResponse().getStatus()).as("a replayed reservation answers 200").isEqualTo(200);
        assertThat(parse(reservationReplay).get("id").asString()).isEqualTo(reservationId);
        assertThat(post(acme, key, "res-2", "/api/v1/reservations", HttpWorld.Map2.of("walletId", wallet, "amount", 6_001, "currency", "USD"))
                .getResponse().getStatus()).as("more than is available").isEqualTo(402);

        MvcResult capture = post(acme, key, "cap-1", "/api/v1/reservations/" + reservationId + "/capture",
                HttpWorld.Map2.of("amount", 2_500, "currency", "USD", "reference", "vm-1 final"));
        assertThat(capture.getResponse().getStatus()).isEqualTo(201);
        assertThat(parse(capture).get("status").asString()).isEqualTo("SUCCEEDED");
        MvcResult captureReplay = post(acme, key, "cap-1", "/api/v1/reservations/" + reservationId + "/capture",
                HttpWorld.Map2.of("amount", 2_500, "currency", "USD", "reference", "vm-1 final"));
        assertThat(captureReplay.getResponse().getStatus()).as("a replayed capture answers 200").isEqualTo(200);
        assertThat(parse(captureReplay).get("id").asString()).isEqualTo(parse(capture).get("id").asString());

        JsonNode afterCapture = world.body(world.call(HttpMethod.GET, acme.host(), key, "/api/v1/wallets/" + wallet, null));
        assertThat(afterCapture.get("balance").get("amount").asLong()).isEqualTo(7_500);
        assertThat(afterCapture.get("held").get("amount").asLong()).isZero();
        assertThat(afterCapture.get("available").get("amount").asLong()).isEqualTo(7_500);

        JsonNode charge = parse(post(acme, key, "chg-1", "/api/v1/charges",
                HttpWorld.Map2.of("walletId", wallet, "amount", 500, "currency", "USD", "usageRef", "usage-42")));
        assertThat(charge.get("status").asString()).isEqualTo("SUCCEEDED");
        assertThat(charge.get("usageRef").asString()).isEqualTo("usage-42");
        MvcResult chargeReplay = post(acme, key, "chg-1", "/api/v1/charges",
                HttpWorld.Map2.of("walletId", wallet, "amount", 500, "currency", "USD", "usageRef", "usage-42"));
        assertThat(chargeReplay.getResponse().getStatus()).as("a replayed charge answers 200").isEqualTo(200);
        assertThat(parse(chargeReplay).get("id").asString()).isEqualTo(charge.get("id").asString());
        assertThat(world.body(world.call(HttpMethod.GET, acme.host(), key, "/api/v1/charges/" + charge.get("id").asString(), null)).get("status").asString())
                .isEqualTo("SUCCEEDED");
        assertThat(post(acme, key, "chg-1", "/api/v1/charges", HttpWorld.Map2.of("walletId", wallet, "amount", 501, "currency", "USD", "usageRef", "usage-42"))
                .getResponse().getStatus()).as("same key, different amount").isEqualTo(409);
        assertThat(post(acme, key, "chg-x", "/api/v1/charges", HttpWorld.Map2.of("walletId", wallet, "amount", 5, "currency", "EUR"))
                .getResponse().getStatus()).as("wrong currency").isEqualTo(400);
    }

    @Test
    void aChargeTheWalletCannotPayIsARecordedFailureWith402() {
        String wallet = openWallet("USD", "PREPAID");
        credit(wallet, 100, "USD");

        MvcResult failed = post(acme, key, "broke-1", "/api/v1/charges", HttpWorld.Map2.of("walletId", wallet, "amount", 101, "currency", "USD"));
        MvcResult replay = post(acme, key, "broke-1", "/api/v1/charges", HttpWorld.Map2.of("walletId", wallet, "amount", 101, "currency", "USD"));

        assertThat(failed.getResponse().getStatus()).isEqualTo(402);
        assertThat(parse(failed).get("status").asString()).isEqualTo("FAILED");
        assertThat(parse(failed).get("failureReason").asString()).isEqualTo("insufficient_funds");
        assertThat(replay.getResponse().getStatus()).isEqualTo(402);
        assertThat(parse(replay).get("id").asString()).isEqualTo(parse(failed).get("id").asString());
        assertThat(world.body(world.call(HttpMethod.GET, acme.host(), key, "/api/v1/wallets/" + wallet, null)).get("balance").get("amount").asLong()).isEqualTo(100);
    }

    @Test
    void theLedgerIsReadInPagesInSequenceOrder() {
        String wallet = openWallet("USD", "PREPAID");
        for (int i = 0; i < 5; i++) {
            credit(wallet, 10, "USD");
        }
        JsonNode firstPage = world.body(world.call(HttpMethod.GET, acme.host(), key, "/api/v1/wallets/" + wallet + "/ledger?limit=3", null));
        assertThat(firstPage).hasSize(3);
        assertThat(firstPage.get(0).get("sequence").asLong()).isEqualTo(1);
        JsonNode secondPage = world.body(world.call(HttpMethod.GET, acme.host(), key,
                "/api/v1/wallets/" + wallet + "/ledger?afterSequence=3&limit=3", null));
        assertThat(secondPage).hasSize(2);
        assertThat(secondPage.get(0).get("sequence").asLong()).isEqualTo(4);
        assertThat(secondPage.get(0).get("type").asString()).isEqualTo("CREDIT");
        assertThat(secondPage.get(0).get("amount").get("amount").asLong()).isEqualTo(10);
        assertThat(world.body(world.call(HttpMethod.GET, acme.host(), key, "/api/v1/wallets/" + wallet + "/ledger?limit=0", null)))
                .as("a page size below one is raised to one")
                .hasSize(1);
    }

    @Test
    void onlyAdministratorsWithThePermissionCanCorrectTheLedger() {
        String wallet = openWallet("USD", "PREPAID");
        credit(wallet, 10_000, "USD");
        JsonNode charge = parse(post(acme, key, "wrong-" + UUID.randomUUID(), "/api/v1/charges",
                HttpWorld.Map2.of("walletId", wallet, "amount", 6_000, "currency", "USD", "reference", "charge:wrong")));
        String entryId = charge.get("ledgerEntryId").asString();

        assertThat(world.status(HttpMethod.POST, acme.host(), key, "/api/v1/ledger-entries/" + entryId + "/reverse", HttpWorld.Map2.of("reason", "x")))
                .as("a tenant system key cannot reverse").isEqualTo(403);
        String systemId = world.body(world.call(HttpMethod.POST, acme.host(), acme.adminToken(), "/api/v1/tenant-systems", HttpWorld.Map2.of("name", "S" + UUID.randomUUID()))).get("id").asString();
        assertThat(world.status(HttpMethod.POST, acme.host(), acme.adminToken(), "/api/v1/tenant-systems/" + systemId + "/credentials",
                HttpWorld.Map2.of("label", "x", "scopes", List.of("ledger:reverse")))).as("the permission cannot be granted to a machine").isEqualTo(400);
        String viewer = world.newUserToken(acme, Set.of(viewerRole()));
        assertThat(world.status(HttpMethod.POST, acme.host(), viewer, "/api/v1/ledger-entries/" + entryId + "/reverse", HttpWorld.Map2.of("reason", "x"))).isEqualTo(403);

        MvcResult reversed = world.call(HttpMethod.POST, acme.host(), acme.adminToken(), "/api/v1/ledger-entries/" + entryId + "/reverse",
                HttpWorld.Map2.of("reason", "wrong_price", "replacement", HttpWorld.Map2.of("type", "DEBIT", "amount", 4_000, "currency", "USD")));
        assertThat(reversed.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = parse(reversed);
        assertThat(body.get("reversal").get("type").asString()).isEqualTo("REVERSAL");
        assertThat(body.get("reversal").get("reversalOf").asString()).isEqualTo(entryId);
        assertThat(body.get("reversal").get("amount").get("amount").asLong()).isEqualTo(6_000);
        assertThat(body.get("replacement").get("amount").get("amount").asLong()).isEqualTo(-4_000);
        assertThat(world.body(world.call(HttpMethod.GET, acme.host(), key, "/api/v1/wallets/" + wallet, null)).get("balance").get("amount").asLong()).isEqualTo(6_000);
        assertThat(world.status(HttpMethod.POST, acme.host(), acme.adminToken(), "/api/v1/ledger-entries/" + entryId + "/reverse",
                HttpWorld.Map2.of("reason", "wrong_price"))).as("reversing twice replays the first reversal").isEqualTo(200);
        assertThat(world.status(HttpMethod.POST, acme.host(), acme.adminToken(), "/api/v1/ledger-entries/" + entryId + "/reverse", HttpWorld.Map2.of("reason", " "))).isEqualTo(400);
    }

    @Test
    void permissionsGateEveryMoneyEndpoint() {
        String wallet = openWallet("USD", "PREPAID");
        credit(wallet, 1_000, "USD");
        String viewer = world.newUserToken(acme, Set.of(viewerRole()));
        String nobody = world.newUserToken(acme, Set.of());
        String reservation = parse(post(acme, key, "perm-res-" + UUID.randomUUID(), "/api/v1/reservations",
                HttpWorld.Map2.of("walletId", wallet, "amount", 10, "currency", "USD"))).get("id").asString();

        assertThat(world.status(HttpMethod.GET, acme.host(), viewer, "/api/v1/wallets/" + wallet, null)).isEqualTo(200);
        assertThat(world.status(HttpMethod.GET, acme.host(), viewer, "/api/v1/wallets/" + wallet + "/ledger", null)).isEqualTo(200);
        assertThat(world.status(HttpMethod.GET, acme.host(), viewer, "/api/v1/reservations/" + reservation, null)).isEqualTo(200);
        assertThat(post(acme, viewer, "v-" + UUID.randomUUID(), "/api/v1/charges", HttpWorld.Map2.of("walletId", wallet, "amount", 1, "currency", "USD")).getResponse().getStatus()).isEqualTo(403);
        assertThat(post(acme, viewer, "v-" + UUID.randomUUID(), "/api/v1/reservations", HttpWorld.Map2.of("walletId", wallet, "amount", 1, "currency", "USD")).getResponse().getStatus()).isEqualTo(403);
        assertThat(world.status(HttpMethod.POST, acme.host(), viewer, "/api/v1/reservations/" + reservation + "/release", null)).isEqualTo(403);
        assertThat(world.status(HttpMethod.POST, acme.host(), viewer, "/api/v1/wallets", HttpWorld.Map2.of("customerId", customerId, "currency", "USD", "mode", "PREPAID"))).isEqualTo(403);

        assertThat(world.status(HttpMethod.GET, acme.host(), nobody, "/api/v1/wallets/" + wallet, null)).isEqualTo(403);
        assertThat(world.status(HttpMethod.GET, acme.host(), nobody, "/api/v1/wallets?customerId=" + customerId, null)).isEqualTo(403);

        assertThat(world.status(HttpMethod.GET, acme.host(), readOnlyKey, "/api/v1/wallets/" + wallet, null)).isEqualTo(200);
        assertThat(post(acme, readOnlyKey, "r-" + UUID.randomUUID(), "/api/v1/charges", HttpWorld.Map2.of("walletId", wallet, "amount", 1, "currency", "USD")).getResponse().getStatus()).isEqualTo(403);
        assertThat(world.status(HttpMethod.POST, acme.host(), readOnlyKey, "/api/v1/reservations/" + reservation + "/release", null)).isEqualTo(403);
        assertThat(world.status(HttpMethod.POST, acme.host(), key, "/api/v1/reservations/" + reservation + "/release", null)).isEqualTo(200);
        assertThat(world.status(HttpMethod.POST, acme.host(), key, "/api/v1/reservations/" + reservation + "/release", null)).as("releasing twice is harmless").isEqualTo(200);
    }

    @Test
    void anotherTenantCannotSeeOrUseAWallet() {
        String wallet = openWallet("USD", "PREPAID");
        credit(wallet, 1_000, "USD");
        assertThat(world.status(HttpMethod.GET, globex.host(), globex.adminToken(), "/api/v1/wallets/" + wallet, null)).isEqualTo(404);
        assertThat(world.status(HttpMethod.GET, globex.host(), globex.adminToken(), "/api/v1/wallets/" + wallet + "/ledger", null)).isEqualTo(404);
        assertThat(world.status(HttpMethod.GET, acme.host(), acme.adminToken(), "/api/v1/wallets?customerId=" + customerId, null)).isEqualTo(200);
        assertThat(world.body(world.call(HttpMethod.GET, globex.host(), globex.adminToken(), "/api/v1/wallets?customerId=" + customerId, null))).isEmpty();
    }

    private String viewerRole() {
        for (JsonNode role : world.body(world.call(HttpMethod.GET, acme.host(), acme.adminToken(), "/api/v1/roles", null))) {
            if (role.get("name").asString().equals("Tenant Viewer")) {
                return role.get("id").asString();
            }
        }
        throw new IllegalStateException("no viewer role");
    }
}
