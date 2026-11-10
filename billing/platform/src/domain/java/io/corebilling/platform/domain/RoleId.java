package io.corebilling.platform.domain;

import java.util.Objects;
import java.util.UUID;

public record RoleId(UUID value) {

    public RoleId {
        Objects.requireNonNull(value, "value");
    }

    public static RoleId of(String text) {
        return new RoleId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
