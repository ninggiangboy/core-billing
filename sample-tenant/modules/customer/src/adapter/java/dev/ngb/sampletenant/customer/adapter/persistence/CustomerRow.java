package dev.ngb.sampletenant.customer.adapter.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("customers")
record CustomerRow(
        @Id UUID id,
        UUID userId,
        String billingCustomerId,
        String displayName,
        String email,
        String status,
        Instant createdAt) {}
