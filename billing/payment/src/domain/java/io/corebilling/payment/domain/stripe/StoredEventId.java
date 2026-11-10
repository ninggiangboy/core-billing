package io.corebilling.payment.domain.stripe;

import java.util.Objects;
import java.util.UUID;

public record StoredEventId(UUID value) {

    public StoredEventId {
        Objects.requireNonNull(value, "value");
    }

    public static StoredEventId of(String text) {
        return new StoredEventId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
