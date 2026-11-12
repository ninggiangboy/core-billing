package dev.ngb.billing.usage.application.usage.query;

import dev.ngb.billing.usage.api.lookup.UsageTotal;
import java.math.BigDecimal;

/** A metric's summed quantity and event count over a period. */
public record UsageTotalView(String metric, String unit, BigDecimal quantity, long events) {

    public UsageTotal toTotal() {
        return new UsageTotal(metric, unit, quantity.stripTrailingZeros(), events);
    }
}
