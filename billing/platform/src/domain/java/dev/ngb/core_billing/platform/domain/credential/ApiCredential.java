package dev.ngb.core_billing.platform.domain.credential;

import dev.ngb.core_billing.platform.api.actor.Permission;
import dev.ngb.core_billing.platform.api.tenant.TenantSystemId;
import dev.ngb.core_billing.sharedkernel.TenantId;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

/** A Billing-issued secret that lets one Tenant System call the API with a bounded set of permissions. */
public final class ApiCredential {

    private final CredentialId id;
    private final TenantId tenantId;
    private final TenantSystemId tenantSystemId;
    private final String keyId;
    private final byte[] secretHash;
    private final Set<Permission> scopes;
    private final String label;
    private final Instant createdAt;
    private CredentialStatus status;
    private Instant expiresAt;
    private Instant revokedAt;

    private ApiCredential(
            CredentialId id,
            TenantId tenantId,
            TenantSystemId tenantSystemId,
            String keyId,
            byte[] secretHash,
            Set<Permission> scopes,
            String label,
            CredentialStatus status,
            Instant createdAt,
            Instant expiresAt,
            Instant revokedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.tenantSystemId = Objects.requireNonNull(tenantSystemId, "tenantSystemId");
        this.keyId = Objects.requireNonNull(keyId, "keyId");
        this.secretHash = Arrays.copyOf(Objects.requireNonNull(secretHash, "secretHash"), secretHash.length);
        this.scopes = Set.copyOf(scopes);
        this.scopes.forEach(scope -> {
            if (!scope.grantableToTenantSystem()) {
                throw new IllegalArgumentException(scope.code() + " cannot be granted to a tenant system");
            }
        });
        Objects.requireNonNull(label, "label");
        if (label.length() > 100) {
            throw new IllegalArgumentException("label must be at most 100 characters");
        }
        this.label = label.strip();
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
    }

    public static ApiCredential issue(
            CredentialId id,
            TenantId tenantId,
            TenantSystemId tenantSystemId,
            ApiKey.Generated key,
            Set<Permission> scopes,
            String label,
            Instant now) {
        return new ApiCredential(
                id, tenantId, tenantSystemId, key.keyId(), ApiKey.hash(key.secret()), scopes, label,
                CredentialStatus.ACTIVE, now, null, null);
    }

    public static ApiCredential restore(
            CredentialId id,
            TenantId tenantId,
            TenantSystemId tenantSystemId,
            String keyId,
            byte[] secretHash,
            Set<Permission> scopes,
            String label,
            CredentialStatus status,
            Instant createdAt,
            Instant expiresAt,
            Instant revokedAt) {
        return new ApiCredential(
                id, tenantId, tenantSystemId, keyId, secretHash, scopes, label, status, createdAt, expiresAt, revokedAt);
    }

    /** Constant-time comparison of the presented secret's digest with the stored digest. */
    public boolean matches(String presentedSecret) {
        return MessageDigest.isEqual(ApiKey.hash(presentedSecret), secretHash);
    }

    public boolean isUsable(Instant now) {
        return status == CredentialStatus.ACTIVE && (expiresAt == null || now.isBefore(expiresAt));
    }

    /** Immediate and permanent. */
    public void revoke(Instant now) {
        if (status == CredentialStatus.ACTIVE) {
            status = CredentialStatus.REVOKED;
            revokedAt = now;
        }
    }

    /** Ends the credential's life at {@code at} unless it already ends sooner. Used for rotation overlap. */
    public void expireAt(Instant at) {
        if (expiresAt == null || at.isBefore(expiresAt)) {
            expiresAt = at;
        }
    }

    public CredentialId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public TenantSystemId tenantSystemId() {
        return tenantSystemId;
    }

    public String keyId() {
        return keyId;
    }

    public byte[] secretHash() {
        return Arrays.copyOf(secretHash, secretHash.length);
    }

    public Set<Permission> scopes() {
        return scopes;
    }

    public String label() {
        return label;
    }

    public CredentialStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    @Override
    public String toString() {
        return "ApiCredential[id=" + id + ", keyId=" + keyId + ", status=" + status + "]";
    }
}
