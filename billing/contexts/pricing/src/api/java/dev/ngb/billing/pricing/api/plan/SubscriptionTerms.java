package dev.ngb.billing.pricing.api.plan;

import dev.ngb.billing.sharedkernel.money.Currency;
import java.util.Objects;
import java.util.UUID;

public record SubscriptionTerms(UUID planId, String planCode, Currency currency, BillingCycle cycle) {

    public SubscriptionTerms {
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(planCode, "planCode");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(cycle, "cycle");
    }
}
