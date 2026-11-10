package io.corebilling.pricing.domain.rating;

import io.corebilling.sharedkernel.Money;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import io.corebilling.pricing.domain.plan.PriceLine;

/**
 * A priced result. {@code amount} is what would be charged, in whole minor units; {@code exactAmount} is the
 * unrounded total of the lines; {@code adjustment} says when the minimum or maximum charge replaced the
 * calculated amount.
 */
public record Quote(Money amount, BigDecimal exactAmount, List<PriceLine> lines, Adjustment adjustment) {

    public enum Adjustment { NONE, MINIMUM_CHARGE, MAXIMUM_CHARGE }

    public Quote {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(exactAmount, "exactAmount");
        lines = List.copyOf(lines);
        Objects.requireNonNull(adjustment, "adjustment");
    }
}
