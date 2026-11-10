package dev.ngb.core_billing.platform.domain.user;

import java.util.Objects;
import java.util.UUID;

public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "value");
    }

    public static UserId of(String text) {
        return new UserId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
