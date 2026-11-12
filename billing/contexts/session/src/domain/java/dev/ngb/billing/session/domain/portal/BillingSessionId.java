package dev.ngb.billing.session.domain.portal;

import java.util.Objects;
import java.util.UUID;

public record BillingSessionId(UUID value) {

    public BillingSessionId {
        Objects.requireNonNull(value, "value");
    }

    public static BillingSessionId of(String text) {
        return new BillingSessionId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
