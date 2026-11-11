package dev.ngb.core_billing.pricing.domain.plan;

import java.util.Objects;
import java.util.UUID;

public record PricingPlanId(UUID value) {

    public PricingPlanId {
        Objects.requireNonNull(value, "value");
    }

    public static PricingPlanId of(String text) {
        return new PricingPlanId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
