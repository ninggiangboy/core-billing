package dev.ngb.core_billing.pricing.domain.rating;

import java.util.Objects;
import java.util.UUID;

public record UsageRatingId(UUID value) {

    public UsageRatingId {
        Objects.requireNonNull(value, "value");
    }

    public static UsageRatingId of(String text) {
        return new UsageRatingId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
