package io.corebilling.usage.application.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One recorded usage event as history returns it: the caller's shape, not the aggregate. */
public record UsageEventView(
        UUID id,
        UUID customerId,
        String metric,
        BigDecimal quantity,
        String unit,
        Instant occurredAt,
        Instant recordedAt,
        String idempotencyKey,
        UUID tenantSystemId,
        String resourceRef,
        String sourceEventId) {}
