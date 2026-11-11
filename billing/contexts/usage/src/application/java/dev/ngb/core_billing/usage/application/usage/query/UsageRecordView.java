package dev.ngb.core_billing.usage.application.usage.query;

import dev.ngb.core_billing.sharedkernel.CustomerId;
import dev.ngb.core_billing.usage.api.lookup.UsageEventId;
import dev.ngb.core_billing.usage.api.lookup.UsageRecord;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** The usage a caller of {@code UsageLookup} needs: no trace, no idempotency bookkeeping. */
public record UsageRecordView(UUID id, UUID customerId, String metric, String unit, BigDecimal quantity, Instant occurredAt) {

    public UsageRecord toRecord() {
        return new UsageRecord(
                new UsageEventId(id), new CustomerId(customerId), metric, unit, quantity.stripTrailingZeros(), occurredAt);
    }
}
