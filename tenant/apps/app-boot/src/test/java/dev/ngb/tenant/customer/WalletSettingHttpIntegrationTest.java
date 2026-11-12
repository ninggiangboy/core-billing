package dev.ngb.tenant.customer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.tenant.identity.api.security.Role;
import dev.ngb.tenant.support.HttpIntegrationTest;
import dev.ngb.tenant.support.HttpWorld;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class WalletSettingHttpIntegrationTest extends HttpIntegrationTest {

    @Test
    void readsAndUpdatesTheWalletSetting() {
        String admin = world.adminToken();

        var current = world.body(world.call(HttpMethod.GET, admin, "/api/customers/me/wallet-settings", null));
        assertThat(current.get("mode").asString()).isEqualTo("PREPAID");
        assertThat(current.get("currency").asString()).isEqualTo("USD");
        assertThat(current.get("walletId").asString()).startsWith("wal_");
        assertThat(current.get("updatedAt").asString()).isNotBlank();

        var updated = world.body(world.call(HttpMethod.PUT, admin, "/api/customers/me/wallet-settings",
                HttpWorld.map("mode", "POSTPAID", "currency", "usd", "creditLimit", 50_000)));

        assertThat(updated.get("customerId").asString()).isNotBlank();
        assertThat(updated.get("mode").asString()).isEqualTo("POSTPAID");
        assertThat(updated.get("currency").asString()).isEqualTo("USD");
        assertThat(updated.get("creditLimit").asLong()).isEqualTo(50_000L);
        assertThat(updated.get("walletId").asString()).startsWith("wal_");
        assertThat(updated.get("updatedAt").asString()).isNotBlank();
    }

    @Test
    void refusesAnInvalidCurrency() {
        String admin = world.adminToken();

        var result = world.call(HttpMethod.PUT, admin, "/api/customers/me/wallet-settings",
                HttpWorld.map("mode", "PREPAID", "currency", "dollars", "creditLimit", null));

        world.requireStatus(result, 400);
        assertThat(world.parse(result).get("detail").asString()).isEqualTo("currency must be an ISO 4217 code");
    }

    @Test
    void aViewerCannotChangeTheSetting() {
        String viewer = world.token(Role.VIEWER);

        world.requireStatus(world.call(HttpMethod.PUT, viewer, "/api/customers/me/wallet-settings",
                HttpWorld.map("mode", "PREPAID", "currency", "USD", "creditLimit", null)), 403);
    }
}
