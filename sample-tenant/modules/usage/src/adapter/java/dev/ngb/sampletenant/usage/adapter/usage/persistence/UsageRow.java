package dev.ngb.sampletenant.usage.adapter.usage.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("usage_records")
record UsageRow(
        @Id UUID id,
        UUID orderId,
        String billingCustomerId,
        String metric,
        String quantity,
        String unit,
        String billingEventId,
        String idempotencyKey,
        Instant occurredAt,
        Instant recordedAt) {}
