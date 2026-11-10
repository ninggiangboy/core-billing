package io.corebilling.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.corebilling.platform.adapter.security.InMemoryRateLimiter;
import io.corebilling.platform.api.Permission;
import io.corebilling.platform.api.PermissionScope;
import io.corebilling.platform.api.TenantSystemId;
import io.corebilling.platform.domain.ApiCredential;
import io.corebilling.platform.domain.ApiKey;
import io.corebilling.platform.domain.AuthorizationPolicy;
import io.corebilling.platform.domain.BillingUser;
import io.corebilling.platform.domain.CredentialId;
import io.corebilling.platform.domain.CredentialStatus;
import io.corebilling.platform.domain.Role;
import io.corebilling.platform.domain.RoleId;
import io.corebilling.platform.domain.UserId;
import io.corebilling.sharedkernel.TenantId;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class IdentityDomainTest {

    private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");
    private static final TenantId TENANT = new TenantId(UUID.randomUUID());

    @Test
    void apiKeysRoundTripAndNeverPrintTheirSecret() {
        ApiKey.Generated key = ApiKey.generate(new SecureRandom());

        assertThat(key.plaintext()).matches("cbk_[0-9a-f]{16}_[0-9a-f]{64}");
        ApiKey.Parsed parsed = ApiKey.parse(key.plaintext()).orElseThrow();
        assertThat(parsed.keyId()).isEqualTo(key.keyId());
        assertThat(parsed.secret()).isEqualTo(key.secret());
        assertThat(key.toString()).doesNotContain(key.secret());
        assertThat(parsed.toString()).doesNotContain(parsed.secret());
    }

    @Test
    void malformedKeysAreRejected() {
        for (String bad : new String[] {null, "", "cbk_", "cbk_abc_def", "xyz_0123456789abcdef_" + "a".repeat(64),
                "cbk_0123456789abcdef_" + "g".repeat(64), "cbk_0123456789abcdef_" + "a".repeat(63)}) {
            assertThat(ApiKey.parse(bad)).as(String.valueOf(bad)).isEmpty();
        }
    }

    @Test
    void credentialStoresOnlyADigestAndComparesInConstantTime() {
        ApiKey.Generated key = ApiKey.generate(new SecureRandom());
        ApiCredential credential = issue(key);

        assertThat(credential.matches(key.secret())).isTrue();
        assertThat(credential.matches("0".repeat(64))).isFalse();
        assertThat(credential.secretHash()).hasSize(32).isNotEqualTo(key.secret().getBytes());
        assertThat(credential.toString()).doesNotContain(key.secret());
    }

    @Test
    void credentialLifecycleRevokeAndExpiry() {
        ApiCredential credential = issue(ApiKey.generate(new SecureRandom()));
        assertThat(credential.isUsable(NOW)).isTrue();

        credential.expireAt(NOW.plusSeconds(60));
        credential.expireAt(NOW.plusSeconds(600)); // never extends an earlier expiry
        assertThat(credential.isUsable(NOW.plusSeconds(59))).isTrue();
        assertThat(credential.isUsable(NOW.plusSeconds(60))).isFalse();

        ApiCredential other = issue(ApiKey.generate(new SecureRandom()));
        other.revoke(NOW);
        assertThat(other.status()).isEqualTo(CredentialStatus.REVOKED);
        assertThat(other.isUsable(NOW)).isFalse();
        assertThat(other.revokedAt()).isEqualTo(NOW);
    }

    @Test
    void credentialScopesMustBeGrantableToTenantSystems() {
        ApiKey.Generated key = ApiKey.generate(new SecureRandom());
        assertThatThrownBy(() -> ApiCredential.issue(new CredentialId(UUID.randomUUID()), TENANT,
                        new TenantSystemId(UUID.randomUUID()), key, Set.of(Permission.USER_MANAGE), "x", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void permissionsOnlyApplyInTheirOwnScope() {
        assertThat(AuthorizationPolicy.permits(Set.of(Permission.TENANT_CREATE), Permission.TENANT_CREATE, PermissionScope.PLATFORM)).isTrue();
        assertThat(AuthorizationPolicy.permits(Set.of(Permission.TENANT_CREATE), Permission.TENANT_CREATE, PermissionScope.TENANT)).isFalse();
        assertThat(AuthorizationPolicy.permits(Set.of(), Permission.USER_READ, PermissionScope.TENANT)).isFalse();
        assertThat(AuthorizationPolicy.permits(Set.of(Permission.USER_READ), Permission.USER_READ, PermissionScope.TENANT)).isTrue();
        assertThat(Permission.fromCode("customer:write")).contains(Permission.CUSTOMER_WRITE);
        assertThat(Permission.fromCode("nope")).isEmpty();
    }

    @Test
    void rolesRejectPermissionsFromAnotherScopeAndKeepDefaultsFixed() {
        assertThatThrownBy(() -> Role.create(new RoleId(UUID.randomUUID()), TENANT, PermissionScope.TENANT, "Ops", false,
                        Set.of(Permission.TENANT_CREATE), NOW))
                .isInstanceOf(IllegalArgumentException.class);

        Role custom = Role.create(new RoleId(UUID.randomUUID()), TENANT, PermissionScope.TENANT, " Ops ", false, Set.of(Permission.USER_READ), NOW);
        custom.replacePermissions(Set.of(Permission.ROLE_READ));
        assertThat(custom.name()).isEqualTo("Ops");
        assertThat(custom.permissions()).containsExactly(Permission.ROLE_READ);

        Role system = Role.create(new RoleId(UUID.randomUUID()), TENANT, PermissionScope.TENANT, "Tenant Admin", true, Set.of(Permission.USER_READ), NOW);
        assertThatThrownBy(() -> system.replacePermissions(Set.of())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void usersNormaliseEmailAndRejectForeignPermissions() {
        BillingUser user = BillingUser.create(new UserId(UUID.randomUUID()), TENANT, PermissionScope.TENANT,
                "  Alice@Example.COM ", "Alice", "sub-1", NOW);
        assertThat(user.email()).isEqualTo("alice@example.com");

        user.grant(Permission.USER_READ);
        user.assign(new RoleId(UUID.randomUUID()));
        assertThat(user.directPermissions()).containsExactly(Permission.USER_READ);
        assertThat(user.roleIds()).hasSize(1);
        assertThatThrownBy(() -> user.grant(Permission.TENANT_CREATE)).isInstanceOf(IllegalArgumentException.class);

        for (String bad : new String[] {"", "no-at", "@x.com", "a@", "a b@x.com", "a@@x.com"}) {
            assertThatThrownBy(() -> BillingUser.create(new UserId(UUID.randomUUID()), TENANT, PermissionScope.TENANT, bad, "A", "s", NOW))
                    .as(bad).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rateLimiterAdmitsABurstThenRefills() {
        AtomicLong nanos = new AtomicLong();
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(3, 1.0, 100, nanos::get);

        assertThat(limiter.tryAcquire("k")).isTrue();
        assertThat(limiter.tryAcquire("k")).isTrue();
        assertThat(limiter.tryAcquire("k")).isTrue();
        assertThat(limiter.tryAcquire("k")).isFalse();
        assertThat(limiter.tryAcquire("other")).isTrue();

        nanos.addAndGet(2_000_000_000L);
        assertThat(limiter.tryAcquire("k")).isTrue();
        assertThat(limiter.tryAcquire("k")).isTrue();
        assertThat(limiter.tryAcquire("k")).isFalse();
    }

    @Test
    void rateLimiterTracksABoundedNumberOfKeys() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(1, 0.0, 2, () -> 0L);
        limiter.tryAcquire("a");
        limiter.tryAcquire("b");
        limiter.tryAcquire("c"); // evicts "a"
        assertThat(limiter.tryAcquire("a")).as("a was forgotten and starts with a full bucket").isTrue();
        assertThat(limiter.tryAcquire("c")).as("c was still tracked and is empty").isFalse();
    }

    private static ApiCredential issue(ApiKey.Generated key) {
        return ApiCredential.issue(new CredentialId(UUID.randomUUID()), TENANT, new TenantSystemId(UUID.randomUUID()),
                key, Set.of(Permission.CUSTOMER_READ), "ci", NOW);
    }
}
