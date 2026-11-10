package io.corebilling.platform.adapter.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("api_credentials")
record CredentialRow(
        @Id UUID id,
        UUID tenantId,
        UUID tenantSystemId,
        String keyId,
        byte[] secretHash,
        String[] scopes,
        String label,
        String status,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt) {}
