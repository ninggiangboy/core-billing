package dev.ngb.core_billing.usage.api.lookup;

import java.math.BigDecimal;
import java.util.Objects;

/** The sum of a metric's quantities over a period and how many events made it up. */
public record UsageTotal(String metric, String unit, BigDecimal quantity, long events) {

    public UsageTotal {
        Objects.requireNonNull(metric, "metric");
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(quantity, "quantity");
    }
}
