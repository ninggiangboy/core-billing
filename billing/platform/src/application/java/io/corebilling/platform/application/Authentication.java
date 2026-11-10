package io.corebilling.platform.application;

import io.corebilling.platform.api.Actor;
import io.corebilling.platform.api.ActorType;
import io.corebilling.platform.api.Permission;
import io.corebilling.platform.api.PermissionScope;
import io.corebilling.platform.api.RequestScope;
import io.corebilling.platform.api.TenantContext;
import io.corebilling.platform.domain.ApiCredential;
import io.corebilling.platform.domain.ApiKey;
import io.corebilling.platform.domain.AuthorizationPolicy;
import io.corebilling.platform.domain.BillingUser;
import io.corebilling.platform.domain.Role;
import io.corebilling.platform.domain.TenantSystem;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.UnitOfWork;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Turns proof of identity into an {@link Actor} for the current scope. Both paths answer "nobody" for
 * anything unknown, revoked, expired or disabled, and never say which of those it was.
 */
public class Authentication {

    private static final byte[] NO_SECRET = new byte[32];

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
        if (!rateLimiter.tryAcquire(key.keyId())) {
            throw new RateLimitExceededException();
        }
        return unitOfWork.transactional(() -> {
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
        });
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
