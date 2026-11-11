package dev.ngb.core_billing.pricing.api.plan;

import dev.ngb.core_billing.sharedkernel.Currency;
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
