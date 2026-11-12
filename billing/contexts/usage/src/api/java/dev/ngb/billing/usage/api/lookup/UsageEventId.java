package dev.ngb.billing.usage.api.lookup;

import java.util.Objects;
import java.util.UUID;

public record UsageEventId(UUID value) {

    public UsageEventId {
        Objects.requireNonNull(value, "value");
    }

    public static UsageEventId of(String text) {
        return new UsageEventId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
