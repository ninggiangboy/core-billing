package dev.ngb.core_billing.session.domain.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ngb.core.id.TenantId;
import dev.ngb.core_billing.platform.api.tenant.TenantSystemId;
import dev.ngb.core_billing.session.api.portal.PortalPermission;
import dev.ngb.core_billing.session.application.portal.service.ReturnUrlPolicy;
import dev.ngb.core_billing.sharedkernel.id.CustomerId;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BillingSessionTest {

    private static final TenantId TENANT = new TenantId(UUID.randomUUID());
    private static final CustomerId CUSTOMER = new CustomerId(UUID.randomUUID());
    private static final TenantSystemId SYSTEM = new TenantSystemId(UUID.randomUUID());
    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");

    @Test
    void anExchangeTokenWorksOnceAndBindsTheSessionToTheCookieToken() {
        BillingSession session = session(NOW.plus(Duration.ofHours(1)));

        session.exchange("cookie-hash", NOW.plusSeconds(5));

        assertThat(session.isExchanged()).isTrue();
        assertThat(session.cookieTokenHash()).isEqualTo("cookie-hash");
        assertThatThrownBy(() -> session.exchange("another-cookie-hash", NOW.plusSeconds(10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already used");
    }

    @Test
    void revocationIsImmediateAndRepeatable() {
        BillingSession session = session(NOW.plus(Duration.ofHours(1)));

        session.revoke(NOW.plusSeconds(1));
        session.revoke(NOW.plusSeconds(2));

        assertThat(session.status()).isEqualTo(SessionStatus.REVOKED);
        assertThat(session.isUsable(NOW.plusSeconds(3))).isFalse();
        assertThat(session.revokedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void anExpiredSessionCannotBeExchanged() {
        BillingSession session = session(NOW.plusSeconds(10));

        assertThat(session.isUsable(NOW.plusSeconds(11))).isFalse();
        assertThatThrownBy(() -> session.exchange("cookie-hash", NOW.plusSeconds(11)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no longer usable");
    }

    @Test
    void aSessionNeedsAtLeastOnePermissionAndAUsableLifetime() {
        assertThatThrownBy(() -> BillingSession.create(
                new BillingSessionId(UUID.randomUUID()), TENANT, CUSTOMER, SYSTEM, Set.of(), "https://app.example.com",
                "hash", NOW, NOW.plusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BillingSession.create(
                new BillingSessionId(UUID.randomUUID()), TENANT, CUSTOMER, SYSTEM, Set.of(PortalPermission.CUSTOMER_VIEW),
                "https://app.example.com", "hash", NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theReturnUrlAllowlistMatchesOnlyTheSameOriginAndPathPrefix() {
        Set<String> allowlist = Set.of("https://app.acme.test/portal");

        ReturnUrlPolicy.requireAllowed("https://app.acme.test/portal/done", allowlist);
        ReturnUrlPolicy.requireAllowed("https://app.acme.test/portal", allowlist);
        assertThatThrownBy(() -> ReturnUrlPolicy.requireAllowed("https://evil.example.com/portal/done", allowlist))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReturnUrlPolicy.requireAllowed("https://app.acme.test/other", allowlist))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReturnUrlPolicy.requireAllowed("http://app.acme.test/portal/done", allowlist))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReturnUrlPolicy.requireAllowed("https://user@app.acme.test/portal", allowlist))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theAllowlistStoresACanonicalOriginWithItsPath() {
        assertThat(ReturnUrlPolicy.requireValidEntry("HTTPS://App.Acme.Test:443/portal/")).isEqualTo("https://app.acme.test/portal/");
        assertThat(ReturnUrlPolicy.requireValidEntry("https://app.acme.test:8443/portal")).isEqualTo("https://app.acme.test:8443/portal");
    }

    private static BillingSession session(Instant expiresAt) {
        return BillingSession.create(
                new BillingSessionId(UUID.randomUUID()), TENANT, CUSTOMER, SYSTEM, PortalPermission.defaultReads(),
                "https://app.acme.test/portal", "exchange-hash", NOW, expiresAt);
    }
}
