package io.corebilling.platform.domain;

import java.util.Objects;
import java.util.UUID;

public record CredentialId(UUID value) {

    public CredentialId {
        Objects.requireNonNull(value, "value");
    }

    public static CredentialId of(String text) {
        return new CredentialId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
