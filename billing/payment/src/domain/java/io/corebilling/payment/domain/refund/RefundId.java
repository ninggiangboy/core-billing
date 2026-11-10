package io.corebilling.payment.domain.refund;

import java.util.Objects;
import java.util.UUID;

public record RefundId(UUID value) {

    public RefundId {
        Objects.requireNonNull(value, "value");
    }

    public static RefundId of(String text) {
        return new RefundId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
