package dev.ngb.billing.pricing.domain.rule;

import java.math.BigDecimal;

/** One band of a tiered price: quantities up to {@code upTo} (unbounded when null) cost {@code unitPrice} each. */
public record Tier(BigDecimal upTo, BigDecimal unitPrice) {

    public Tier {
        Prices.price(unitPrice, "tier unitPrice");
        if (upTo != null && upTo.signum() <= 0) {
            throw new IllegalArgumentException("a tier limit must be greater than zero");
        }
    }
}
