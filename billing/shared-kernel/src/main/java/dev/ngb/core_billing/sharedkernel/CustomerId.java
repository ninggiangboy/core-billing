package dev.ngb.core_billing.sharedkernel;

import java.util.Objects;
import java.util.UUID;

/** Identifier of a Customer. A value object so a raw UUID is never threaded through business code. */
public record CustomerId(UUID value) {

    public CustomerId {
        Objects.requireNonNull(value, "value");
    }

    public static CustomerId of(String text) {
        return new CustomerId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
