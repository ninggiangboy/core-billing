package dev.ngb.sampletenant.usage.application.usage.query;

import java.time.Instant;
import java.util.UUID;

/** One usage event as the tenant's screens render it. */
public record UsageRecordView(
        UUID id,
        UUID orderId,
        String metric,
        String quantity,
        String unit,
        String billingEventId,
        Instant occurredAt,
        Instant recordedAt) {}
