package io.corebilling.money.api;

import java.util.Objects;
import java.util.UUID;

public record ReservationId(UUID value) {

    public ReservationId {
        Objects.requireNonNull(value, "value");
    }

    public static ReservationId of(String text) {
        return new ReservationId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
