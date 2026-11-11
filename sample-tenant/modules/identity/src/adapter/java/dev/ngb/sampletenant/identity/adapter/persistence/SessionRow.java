package dev.ngb.sampletenant.identity.adapter.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("user_sessions")
record SessionRow(
        @Id UUID id,
        UUID userId,
        String tokenHash,
        Instant createdAt,
        Instant expiresAt) {}
