package dev.ngb.billing.pricing.domain.rule;

import java.math.BigDecimal;
import java.util.Objects;

/** One line of a price breakdown. {@code amount} is exact, in minor units, and not yet rounded. */
public record PriceLine(String description, String metric, BigDecimal quantity, BigDecimal amount) {

    public PriceLine {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(amount, "amount");
        quantity = quantity == null ? null : canonical(quantity);
        amount = canonical(amount);
    }

    /** One representation per value (10, not 1E+1 or 10.00), so a line read back from storage equals the one that was written. */
    private static BigDecimal canonical(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
