package io.corebilling.identity;

import io.corebilling.platform.api.error.ConflictException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.corebilling.Application;
import io.corebilling.platform.api.actor.Actor;
import io.corebilling.platform.api.actor.ActorType;
import io.corebilling.platform.api.actor.Permission;
import io.corebilling.platform.application.auth.query.Authentication;
import io.corebilling.platform.application.auth.query.Caller;
import io.corebilling.platform.application.credential.command.CredentialAdministration;
import io.corebilling.platform.application.auth.query.RateLimitExceededException;
import io.corebilling.platform.application.tenantsystem.command.TenantSystemAdministration;
import io.corebilling.platform.domain.tenant.Tenant;
import io.corebilling.platform.domain.tenantsystem.TenantSystem;
import io.corebilling.support.OwnerDatabase;
import io.corebilling.support.PostgresContainerConfig;
import io.corebilling.support.ScopeTestSupport;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(
        classes = Application.class,
        properties = {"billing.security.api-key-rate-limit.burst=5", "billing.security.api-key-rate-limit.per-second=0"})
@Import(PostgresContainerConfig.class)
class CredentialLifecycleIntegrationTest {

    private static final Caller CALLER = new Caller("user/admin", "req-1");

    @Autowired TenantSystemAdministration systems;
    @Autowired CredentialAdministration credentials;
    @Autowired Authentication authentication;
    @Autowired OwnerDatabase owner;
    @Autowired ScopeTestSupport scopes;

    private record Setup(Tenant tenant, TenantSystem system, CredentialAdministration.Issued issued) {}

    private Setup setup() {
        Tenant tenant = scopes.newTenant();
        return scopes.inTenant(tenant.id(), () -> {
            TenantSystem system = systems.create(CALLER, "Sample " + System.nanoTime());
            CredentialAdministration.Issued issued =
                    credentials.create(CALLER, system.id(), "ci", Set.of(Permission.CUSTOMER_READ));
            return new Setup(tenant, system, issued);
        });
    }

    private Optional<Actor> authenticate(Tenant tenant, String key) {
        return scopes.inTenant(tenant.id(), () -> authentication.authenticateApiKey(key));
    }

    @Test
    void aValidKeyAuthenticatesAsItsTenantSystemWithItsScopes() {
        Setup s = setup();

        Actor actor = authenticate(s.tenant(), s.issued().plaintextKey()).orElseThrow();

        assertThat(actor.type()).isEqualTo(ActorType.TENANT_SYSTEM);
        assertThat(actor.tenantId()).isEqualTo(s.tenant().id());
        assertThat(actor.tenantSystemId()).isEqualTo(s.system().id());
        assertThat(actor.permissions()).containsExactly(Permission.CUSTOMER_READ);
    }

    @Test
    void theSecretIsNeverStoredOrAudited() {
        Setup s = setup();
        String secret = s.issued().plaintextKey().substring(s.issued().plaintextKey().lastIndexOf('_') + 1);

        assertThat(owner.count("select count(*) from api_credentials where tenant_id = ? and position(? in encode(secret_hash, 'hex')) > 0",
                        s.tenant().id().value(), secret))
                .as("only a digest is stored, never the secret itself")
                .isZero();
        assertThat(owner.count("select count(*) from audit_log where tenant_id = ? and (subject like ? or action like ?)",
                        s.tenant().id().value(), "%" + secret + "%", "%" + secret + "%"))
                .isZero();
        assertThat(owner.count("select count(*) from audit_log where tenant_id = ? and action = 'credential.created'", s.tenant().id().value()))
                .isEqualTo(1);
    }

    @Test
    void wrongMalformedAndForeignKeysAreRejectedIndistinguishably() {
        Setup s = setup();
        Setup other = setup();
        String key = s.issued().plaintextKey();
        String keyId = key.substring(4, 20);

        assertThat(authenticate(s.tenant(), "cbk_" + keyId + "_" + "0".repeat(64))).isEmpty();
        assertThat(authenticate(s.tenant(), "not-a-key")).isEmpty();
        assertThat(authenticate(other.tenant(), key)).as("a key presented on another tenant").isEmpty();
    }

    @Test
    void revocationIsImmediateAndAudited() {
        Setup s = setup();
        assertThat(authenticate(s.tenant(), s.issued().plaintextKey())).isPresent();

        scopes.runInTenant(s.tenant().id(), () -> credentials.revoke(CALLER, s.issued().credential().id()));

        assertThat(authenticate(s.tenant(), s.issued().plaintextKey())).isEmpty();
        assertThat(owner.count("select count(*) from audit_log where tenant_id = ? and action = 'credential.revoked'", s.tenant().id().value()))
                .isEqualTo(1);
    }

    @Test
    void rotationKeepsBothKeysUntilTheOverlapEnds() throws InterruptedException {
        Setup s = setup();

        CredentialAdministration.Issued next = scopes.inTenant(s.tenant().id(),
                () -> credentials.rotate(CALLER, s.system().id(), CredentialAdministration.MIN_OVERLAP));

        assertThat(next.credential().scopes()).containsExactly(Permission.CUSTOMER_READ);
        assertThat(authenticate(s.tenant(), s.issued().plaintextKey())).as("old key during overlap").isPresent();
        assertThat(authenticate(s.tenant(), next.plaintextKey())).as("new key").isPresent();

        assertThatThrownBy(() -> scopes.inTenant(s.tenant().id(),
                        () -> credentials.rotate(CALLER, s.system().id(), CredentialAdministration.MIN_OVERLAP)))
                .as("a second rotation while one overlap is open")
                .isInstanceOf(ConflictException.class);

        // End the overlap by moving the old key's expiry into the past.
        owner.jdbc().update("update api_credentials set expires_at = now() - interval '1 second' where id = ?",
                s.issued().credential().id().value());
        assertThat(authenticate(s.tenant(), s.issued().plaintextKey())).as("old key after overlap").isEmpty();
        assertThat(authenticate(s.tenant(), next.plaintextKey())).isPresent();
        assertThat(owner.count("select count(*) from audit_log where tenant_id = ? and action = 'credential.rotated'", s.tenant().id().value()))
                .isEqualTo(1);
    }

    @Test
    void overlapIsBounded() {
        Setup s = setup();
        assertThatThrownBy(() -> scopes.inTenant(s.tenant().id(), () -> credentials.rotate(CALLER, s.system().id(), Duration.ofDays(30))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scopes.inTenant(s.tenant().id(), () -> credentials.rotate(CALLER, s.system().id(), Duration.ZERO)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scopesMustBeGrantableToTenantSystems() {
        Setup s = setup();
        assertThatThrownBy(() -> scopes.inTenant(s.tenant().id(),
                        () -> credentials.create(CALLER, s.system().id(), "bad", Set.of(Permission.USER_MANAGE))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aKeyIsRateLimitedPerKeyId() {
        Setup s = setup();
        String key = s.issued().plaintextKey();
        for (int i = 0; i < 5; i++) {
            assertThat(authenticate(s.tenant(), key)).isPresent();
        }
        assertThatThrownBy(() -> authenticate(s.tenant(), key)).isInstanceOf(RateLimitExceededException.class);
    }
}
