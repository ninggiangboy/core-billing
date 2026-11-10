package dev.ngb.core_billing.money.api.charge;

import java.util.Objects;
import java.util.UUID;

public record ChargeId(UUID value) {

    public ChargeId {
        Objects.requireNonNull(value, "value");
    }

    public static ChargeId of(String text) {
        return new ChargeId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
