package dev.ngb.sampletenant.identity.adapter.user.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("users")
record UserRow(
        @Id UUID id,
        String email,
        String displayName,
        String passwordHash,
        String role,
        String status,
        Instant createdAt) {}
