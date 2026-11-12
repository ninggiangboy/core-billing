package dev.ngb.billing.invoicing.domain.invoice;

import java.util.Objects;
import java.util.UUID;

public record InvoiceId(UUID value) {

    public InvoiceId {
        Objects.requireNonNull(value, "value");
    }

    public static InvoiceId of(String text) {
        return new InvoiceId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
