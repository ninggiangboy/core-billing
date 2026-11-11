package dev.ngb.core_billing.payment.domain.stripe;

import java.util.Objects;
import java.util.UUID;

public record StripeEventId(UUID value) {

    public StripeEventId {
        Objects.requireNonNull(value, "value");
    }

    public static StripeEventId of(String text) {
        return new StripeEventId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
