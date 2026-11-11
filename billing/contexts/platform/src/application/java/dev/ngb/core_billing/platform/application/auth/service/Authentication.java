package dev.ngb.core_billing.platform.application.auth.service;

import dev.ngb.core_billing.platform.api.actor.Actor;
import dev.ngb.core_billing.platform.api.actor.ActorType;
import dev.ngb.core_billing.platform.api.actor.Permission;
import dev.ngb.core_billing.platform.api.actor.PermissionScope;
import dev.ngb.core_billing.platform.api.actor.RequestScope;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.platform.domain.credential.ApiCredential;
import dev.ngb.core_billing.platform.domain.credential.ApiKey;
import dev.ngb.core_billing.platform.domain.role.AuthorizationPolicy;
import dev.ngb.core_billing.platform.domain.user.BillingUser;
import dev.ngb.core_billing.platform.domain.role.Role;
import dev.ngb.core_billing.platform.domain.tenantsystem.TenantSystem;
import dev.ngb.core_billing.sharedkernel.runtime.Clock;
import dev.ngb.core_billing.sharedkernel.runtime.UnitOfWork;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import dev.ngb.core_billing.platform.application.auth.port.RateLimiter;
import dev.ngb.core_billing.platform.application.credential.port.CredentialRepository;
import dev.ngb.core_billing.platform.application.role.port.RoleRepository;
import dev.ngb.core_billing.platform.application.tenant.exception.TenantMismatchException;
import dev.ngb.core_billing.platform.application.tenant.port.TenantRepository;
import dev.ngb.core_billing.platform.application.tenantsystem.port.TenantSystemRepository;
import dev.ngb.core_billing.platform.application.user.port.UserRepository;
import dev.ngb.core_billing.platform.application.auth.exception.RateLimitExceededException;

/**
 * Turns proof of identity into an {@link Actor} for the current scope. Both paths answer "nobody" for
 * anything unknown, revoked, expired or disabled, and never say which of those it was.
 */
public class Authentication {

    private static final byte[] NO_SECRET = new byte[32];

    /**
     * How many buckets a request that did not authenticate shares, chosen by key id. A caller can invent key ids
     * freely, so without this every unknown id would create a rate-limit row of its own; with it the rows stay
     * bounded and an unknown id cannot exhaust a real key's budget. It must be a power of two.
     */
    private static final int ANONYMOUS_BUCKETS = 1024;

    private static final String ANONYMOUS_BUCKET_PREFIX = "unauthenticated:";

    private final UnitOfWork unitOfWork;
    private final UserRepository users;
    private final RoleRepository roles;
    private final TenantRepository tenants;
    private final CredentialRepository credentials;
    private final TenantSystemRepository systems;
    private final RateLimiter rateLimiter;
    private final TenantContext tenantContext;
    private final Clock clock;

    public Authentication(
            UnitOfWork unitOfWork,
            UserRepository users,
            RoleRepository roles,
            TenantRepository tenants,
            CredentialRepository credentials,
            TenantSystemRepository systems,
            RateLimiter rateLimiter,
            TenantContext tenantContext,
            Clock clock) {
        this.unitOfWork = unitOfWork;
        this.users = users;
        this.roles = roles;
        this.tenants = tenants;
        this.credentials = credentials;
        this.systems = systems;
        this.rateLimiter = rateLimiter;
        this.tenantContext = tenantContext;
        this.clock = clock;
    }

    /**
     * @param subject the validated OIDC subject
     * @param tenantCodeClaim the token's {@code tenant_code} claim
     * @throws TenantMismatchException when the claim names a different tenant than the hostname resolved to
     * @return empty when the subject is not an active user of this scope
     */
    public Optional<Actor> authenticateUser(String subject, String tenantCodeClaim) {
        return unitOfWork.transactional(() -> {
            RequestScope scope = tenantContext.current();
            String expected = tenants.findById(scope.dataTenantId()).map(t -> t.code().value()).orElse(null);
            if (expected == null || !expected.equals(tenantCodeClaim)) {
                throw new TenantMismatchException();
            }
            return users.findByKeycloakSubject(subject).filter(BillingUser::isActive).map(user -> toActor(user));
        });
    }

    /**
     * @throws RateLimitExceededException when the key id exceeded its rate limit
     * @return empty when the key is malformed, unknown in this tenant, wrong, revoked, expired, or its
     *     tenant system is disabled
     */
    public Optional<Actor> authenticateApiKey(String presentedKey) {
        Optional<ApiKey.Parsed> parsed = ApiKey.parse(presentedKey);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        ApiKey.Parsed key = parsed.get();
        Optional<Actor> actor = unitOfWork.transactional(() -> resolveApiKey(key));
        // Only a key that authenticated spends its own bucket. Everything else spends one of a fixed set of shared
        // buckets, so a wrong or invented key id can neither exhaust a real key's budget nor create a row per try.
        String bucket = actor.isPresent() ? key.keyId() : ANONYMOUS_BUCKET_PREFIX + anonymousSlot(key.keyId());
        if (!rateLimiter.tryAcquire(bucket)) {
            throw new RateLimitExceededException();
        }
        return actor;
    }

    private Optional<Actor> resolveApiKey(ApiKey.Parsed key) {
        Optional<ApiCredential> found = credentials.findByKeyId(key.keyId());
        if (found.isEmpty()) {
            // Spend the same work as a real comparison so a missing key id is not detectable by timing.
            MessageDigest.isEqual(ApiKey.hash(key.secret()), NO_SECRET);
            return Optional.empty();
        }
        ApiCredential credential = found.get();
        Instant now = clock.now();
        if (!credential.matches(key.secret()) || !credential.isUsable(now)) {
            return Optional.empty();
        }
        Optional<TenantSystem> system = systems.findById(credential.tenantSystemId()).filter(TenantSystem::isActive);
        return system.map(s -> new Actor(
                ActorType.TENANT_SYSTEM, s.id().value(), s.tenantId(), PermissionScope.TENANT, s.name(), s.id(),
                credential.scopes()));
    }

    private static int anonymousSlot(String keyId) {
        return Integer.parseUnsignedInt(keyId.substring(0, 8), 16) & (ANONYMOUS_BUCKETS - 1);
    }

    private Actor toActor(BillingUser user) {
        Set<Permission> granted = EnumSet.noneOf(Permission.class);
        granted.addAll(user.directPermissions());
        for (Role role : roles.findAllById(user.roleIds())) {
            granted.addAll(role.permissions());
        }
        granted.removeIf(permission -> !AuthorizationPolicy.permits(Set.of(permission), permission, user.scope()));
        return new Actor(
                ActorType.USER, user.id().value(), user.tenantId(), user.scope(), user.displayName(), null, granted);
    }
}
