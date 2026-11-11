package dev.ngb.sampletenant.billing.api.model;

import java.time.Instant;

/** A recorded usage event. */
public record UsageEvent(
        String id,
        String customerId,
        String metric,
        String quantity,
        String unit,
        Instant occurredAt,
        Instant recordedAt,
        String idempotencyKey,
        String tenantSystemId,
        String resourceRef,
        String sourceEventId) {}
