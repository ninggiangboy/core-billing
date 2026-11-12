package dev.ngb.core_billing.session.domain.portal;

import dev.ngb.core.id.TenantId;
import dev.ngb.core_billing.platform.api.tenant.TenantSystemId;
import dev.ngb.core_billing.session.api.portal.PortalPermission;
import dev.ngb.core_billing.sharedkernel.id.CustomerId;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * A time-limited, scoped grant for one Customer to use the Billing Portal. The raw exchange token never enters
 * the aggregate: only its hash is stored, it is valid once, and the exchange replaces it with a separate cookie
 * token whose hash is what later requests are matched against.
 */
public final class BillingSession {

    private final BillingSessionId id;
    private final TenantId tenantId;
    private final CustomerId customerId;
    private final TenantSystemId tenantSystemId;
    private final Set<PortalPermission> permissions;
    private final String returnUrl;
    private final String exchangeTokenHash;
    private final Instant createdAt;
    private final Instant expiresAt;
    private SessionStatus status;
    private String cookieTokenHash;
    private Instant exchangedAt;
    private Instant revokedAt;

    private BillingSession(
            BillingSessionId id,
            TenantId tenantId,
            CustomerId customerId,
            TenantSystemId tenantSystemId,
            Set<PortalPermission> permissions,
            String returnUrl,
            String exchangeTokenHash,
            Instant createdAt,
            Instant expiresAt,
            SessionStatus status,
            String cookieTokenHash,
            Instant exchangedAt,
            Instant revokedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.tenantSystemId = Objects.requireNonNull(tenantSystemId, "tenantSystemId");
        this.permissions = Set.copyOf(permissions);
        this.returnUrl = requireReturnUrl(returnUrl);
        this.exchangeTokenHash = requireHash(exchangeTokenHash, "exchangeTokenHash");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.status = Objects.requireNonNull(status, "status");
        this.cookieTokenHash = cookieTokenHash;
        this.exchangedAt = exchangedAt;
        this.revokedAt = revokedAt;
        if (this.permissions.isEmpty()) {
            throw new IllegalArgumentException("a session must grant at least one permission");
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("a session must expire after it was created");
        }
        if ((status == SessionStatus.REVOKED) != (revokedAt != null)) {
            throw new IllegalArgumentException("only a revoked session carries a revocation time");
        }
        if ((cookieTokenHash != null) != (exchangedAt != null)) {
            throw new IllegalArgumentException("a session is exchanged and carries a cookie token together");
        }
    }

    public static BillingSession create(
            BillingSessionId id,
            TenantId tenantId,
            CustomerId customerId,
            TenantSystemId tenantSystemId,
            Set<PortalPermission> permissions,
            String returnUrl,
            String exchangeTokenHash,
            Instant now,
            Instant expiresAt) {
        return new BillingSession(id, tenantId, customerId, tenantSystemId, permissions, returnUrl, exchangeTokenHash, now, expiresAt,
                SessionStatus.ACTIVE, null, null, null);
    }

    public static BillingSession restore(
            BillingSessionId id,
            TenantId tenantId,
            CustomerId customerId,
            TenantSystemId tenantSystemId,
            Set<PortalPermission> permissions,
            String returnUrl,
            String exchangeTokenHash,
            Instant createdAt,
            Instant expiresAt,
            SessionStatus status,
            String cookieTokenHash,
            Instant exchangedAt,
            Instant revokedAt) {
        return new BillingSession(id, tenantId, customerId, tenantSystemId, permissions, returnUrl, exchangeTokenHash, createdAt, expiresAt,
                status, cookieTokenHash, exchangedAt, revokedAt);
    }

    public boolean isUsable(Instant now) {
        return status == SessionStatus.ACTIVE && now.isBefore(expiresAt);
    }

    public boolean isExchanged() {
        return exchangedAt != null;
    }

    /**
     * Consumes the single-use exchange token and binds the session to a fresh cookie token.
     *
     * @throws IllegalStateException when the session is revoked, expired or already exchanged
     */
    public void exchange(String newCookieTokenHash, Instant now) {
        if (!isUsable(now)) {
            throw new IllegalStateException("the session is no longer usable");
        }
        if (isExchanged()) {
            throw new IllegalStateException("the exchange token was already used");
        }
        this.cookieTokenHash = requireHash(newCookieTokenHash, "cookieTokenHash");
        this.exchangedAt = now;
    }

    /** Revoking twice changes nothing. An expired session can still be revoked so its row stops being ACTIVE. */
    public void revoke(Instant now) {
        if (status == SessionStatus.REVOKED) {
            return;
        }
        this.status = SessionStatus.REVOKED;
        this.revokedAt = now;
    }

    public BillingSessionId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public CustomerId customerId() {
        return customerId;
    }

    public TenantSystemId tenantSystemId() {
        return tenantSystemId;
    }

    public Set<PortalPermission> permissions() {
        return Set.copyOf(permissions);
    }

    public String returnUrl() {
        return returnUrl;
    }

    public String exchangeTokenHash() {
        return exchangeTokenHash;
    }

    public String cookieTokenHash() {
        return cookieTokenHash;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant exchangedAt() {
        return exchangedAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public SessionStatus status() {
        return status;
    }

    private static String requireReturnUrl(String returnUrl) {
        Objects.requireNonNull(returnUrl, "returnUrl");
        if (returnUrl.isBlank()) {
            throw new IllegalArgumentException("a return URL is required");
        }
        return returnUrl;
    }

    private static String requireHash(String hash, String name) {
        Objects.requireNonNull(hash, name);
        if (hash.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return hash;
    }
}
