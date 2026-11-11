package dev.ngb.core_billing.pricing.api.plan;

import dev.ngb.core_billing.sharedkernel.Money;
import java.util.Objects;
import java.util.UUID;

/** The recurring amount of one billing period and the plan version it came from, so the charge can be explained later. */
public record PeriodPrice(UUID planId, String planCode, int version, BillingCycle cycle, Money amount) {

    public PeriodPrice {
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(planCode, "planCode");
        Objects.requireNonNull(cycle, "cycle");
        Objects.requireNonNull(amount, "amount");
    }
}
