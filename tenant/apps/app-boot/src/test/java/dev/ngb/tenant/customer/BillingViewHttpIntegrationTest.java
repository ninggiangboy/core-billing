package dev.ngb.tenant.customer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.tenant.identity.api.security.Role;
import dev.ngb.tenant.support.HttpIntegrationTest;
import dev.ngb.tenant.support.HttpWorld;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

class BillingViewHttpIntegrationTest extends HttpIntegrationTest {

    @Test
    void opensASessionForTheMappedCustomer() {
        String operator = world.token(Role.OPERATOR);

        var session = world.body(world.call(HttpMethod.POST, operator, "/api/billing/session",
                HttpWorld.map("returnUrl", null, "permissions", java.util.List.of("wallet:view"), "expiresInSeconds", 3600)));

        assertThat(session.get("id").asString()).isEqualTo("ses_1");
        assertThat(session.get("customerId").asString()).startsWith("cus_");
        assertThat(session.get("portalUrl").asString()).isEqualTo("https://tenant-a.example/portal#token");
        assertThat(session.get("permissions").get(0).asString()).isEqualTo("wallet:view");
        assertThat(session.get("expiresAt").asString()).isNotBlank();
    }

    @Test
    void readsWalletsInvoicesAndPayments() {
        String operator = world.token(Role.OPERATOR);

        var wallets = world.body(world.call(HttpMethod.GET, operator, "/api/billing/wallets", null));
        assertThat(wallets.isArray()).isTrue();
        assertThat(wallets.size()).isEqualTo(1);
        var wallet = wallets.get(0);
        assertThat(wallet.get("id").asString()).startsWith("wal_");
        assertThat(wallet.get("currency").asString()).isEqualTo("USD");
        assertThat(wallet.get("mode").asString()).isEqualTo("PREPAID");
        assertThat(wallet.get("balance").get("currency").asString()).isEqualTo("USD");

        var invoices = world.body(world.call(HttpMethod.GET, operator, "/api/billing/invoices", null));
        assertThat(invoices.isArray()).isTrue();
        assertThat(invoices.size()).isEqualTo(1);
        assertThat(invoices.get(0).get("number").asString()).isEqualTo("INV-000001");

        var payments = world.body(world.call(HttpMethod.GET, operator, "/api/billing/payments", null));
        assertThat(payments.isArray()).isTrue();
        assertThat(payments.size()).isEqualTo(2);
        assertThat(payments.get(0).get("status").asString()).isEqualTo("SUCCEEDED");
    }

    @Test
    void startsATopUpForAnOwnedWallet() {
        String operator = world.token(Role.OPERATOR);
        var wallets = world.body(world.call(HttpMethod.GET, operator, "/api/billing/wallets", null));
        String walletId = wallets.get(0).get("id").asString();

        MvcResult result = world.call(HttpMethod.POST, operator, "/api/billing/top-ups",
                HttpWorld.map("walletId", walletId, "amount", 500, "currency", "USD"));

        world.requireStatus(result, 200);
        var payment = world.parse(result);
        assertThat(payment.get("id").asString()).isEqualTo("pay_1");
        assertThat(payment.get("walletId").asString()).isEqualTo(walletId);
        assertThat(payment.get("status").asString()).isEqualTo("PENDING");
        assertThat(payment.get("amount").get("amount").asLong()).isEqualTo(500L);
    }

    @Test
    void refusesATopUpForAnotherWallet() {
        String operator = world.token(Role.OPERATOR);

        MvcResult result = world.call(HttpMethod.POST, operator, "/api/billing/top-ups",
                HttpWorld.map("walletId", "wal_other", "amount", 500, "currency", "USD"));

        world.requireStatus(result, 404);
        assertThat(world.parse(result).get("detail").asString()).isEqualTo("wallet not found");
    }

    @Test
    void refusesTheViewsForAnUnmappedCustomer() {
        billing.customersUnavailable();
        try {
            HttpWorld.Account account = world.registerViaApi();

            MvcResult result = world.call(HttpMethod.GET, account.token(), "/api/billing/wallets", null);

            world.requireStatus(result, 400);
            assertThat(world.parse(result).get("detail").asString())
                    .isEqualTo("this account has no Billing customer mapping yet");
        } finally {
            billing.customersRecover();
        }
    }
}
