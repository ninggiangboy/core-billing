package dev.ngb.tenant.billing.api.model;

import java.time.Instant;

/** A usage event to record. The idempotency key makes a retry record nothing twice. */
public record UsageEventRequest(
        String customerId,
        String metric,
        String quantity,
        String unit,
        Instant occurredAt,
        String idempotencyKey,
        String resourceRef,
        String sourceEventId) {}
