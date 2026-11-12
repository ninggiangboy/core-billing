package dev.ngb.billing.platform.domain.credential;

import java.util.Objects;
import java.util.UUID;

public record ApiCredentialId(UUID value) {

    public ApiCredentialId {
        Objects.requireNonNull(value, "value");
    }

    public static ApiCredentialId of(String text) {
        return new ApiCredentialId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
