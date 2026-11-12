package dev.ngb.sampletenant.usage.domain;

import dev.ngb.core.runtime.IdGenerator;
import java.time.Instant;
import java.util.UUID;

/** One usage event this tenant reported, with the id Billing returned for it. */
public record UsageRecord(
        UUID id,
        UUID orderId,
        String billingCustomerId,
        String metric,
        String quantity,
        String unit,
        String billingEventId,
        String idempotencyKey,
        Instant occurredAt,
        Instant recordedAt) {

    public static UsageRecord recorded(
            IdGenerator ids,
            UUID orderId,
            String billingCustomerId,
            String metric,
            String quantity,
            String unit,
            String billingEventId,
            String idempotencyKey,
            Instant occurredAt,
            Instant recordedAt) {
        return new UsageRecord(
                ids.newId(),
                orderId,
                billingCustomerId,
                metric,
                quantity,
                unit,
                billingEventId,
                idempotencyKey,
                occurredAt,
                recordedAt);
    }
}
