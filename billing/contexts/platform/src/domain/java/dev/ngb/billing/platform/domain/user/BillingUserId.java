package dev.ngb.billing.platform.domain.user;

import java.util.Objects;
import java.util.UUID;

public record BillingUserId(UUID value) {

    public BillingUserId {
        Objects.requireNonNull(value, "value");
    }

    public static BillingUserId of(String text) {
        return new BillingUserId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
