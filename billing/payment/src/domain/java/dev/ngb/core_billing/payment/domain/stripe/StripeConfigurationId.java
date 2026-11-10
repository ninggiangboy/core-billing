package dev.ngb.core_billing.payment.domain.stripe;

import java.util.Objects;
import java.util.UUID;

public record StripeConfigurationId(UUID value) {

    public StripeConfigurationId {
        Objects.requireNonNull(value, "value");
    }

    public static StripeConfigurationId of(String text) {
        return new StripeConfigurationId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
