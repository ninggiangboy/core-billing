package dev.ngb.billing.invoicing.domain.subscription;

import java.util.Objects;
import java.util.UUID;

public record SubscriptionId(UUID value) {

    public SubscriptionId {
        Objects.requireNonNull(value, "value");
    }

    public static SubscriptionId of(String text) {
        return new SubscriptionId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
