package dev.ngb.core_billing.usage.api.usage;

import dev.ngb.core_billing.sharedkernel.CustomerId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** One recorded usage event, as other contexts may read it. */
public record UsageRecord(UsageEventId id, CustomerId customerId, String metric, String unit, BigDecimal quantity, Instant occurredAt) {

    public UsageRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(metric, "metric");
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
