package dev.ngb.core_billing.pricing.domain.rating;

import java.util.Objects;
import java.util.UUID;

public record RatingId(UUID value) {

    public RatingId {
        Objects.requireNonNull(value, "value");
    }

    public static RatingId of(String text) {
        return new RatingId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
