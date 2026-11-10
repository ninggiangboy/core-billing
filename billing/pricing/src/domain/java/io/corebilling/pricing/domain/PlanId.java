package io.corebilling.pricing.domain;

import java.util.Objects;
import java.util.UUID;

public record PlanId(UUID value) {

    public PlanId {
        Objects.requireNonNull(value, "value");
    }

    public static PlanId of(String text) {
        return new PlanId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
