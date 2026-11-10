package io.corebilling.payment.domain;

import java.util.Objects;
import java.util.UUID;

public record PaymentId(UUID value) {

    public PaymentId {
        Objects.requireNonNull(value, "value");
    }

    public static PaymentId of(String text) {
        return new PaymentId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
