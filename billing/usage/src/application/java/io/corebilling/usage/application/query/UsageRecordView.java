package io.corebilling.usage.application.query;

import io.corebilling.sharedkernel.CustomerId;
import io.corebilling.usage.api.usage.UsageEventId;
import io.corebilling.usage.api.usage.UsageRecord;
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
