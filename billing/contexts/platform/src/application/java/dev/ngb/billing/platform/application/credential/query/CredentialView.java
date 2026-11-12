package dev.ngb.billing.platform.application.credential.query;

import dev.ngb.billing.platform.api.actor.Permission;
import dev.ngb.billing.platform.domain.credential.CredentialStatus;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Credential metadata; it never carries the secret or its hash. */
public record CredentialView(
        UUID id,
        UUID tenantSystemId,
        String keyId,
        String label,
        Set<Permission> scopes,
        CredentialStatus status,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt) {}
