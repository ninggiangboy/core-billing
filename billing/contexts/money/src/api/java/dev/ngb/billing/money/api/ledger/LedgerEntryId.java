package dev.ngb.billing.money.api.ledger;

import java.util.Objects;
import java.util.UUID;

public record LedgerEntryId(UUID value) {

    public LedgerEntryId {
        Objects.requireNonNull(value, "value");
    }

    public static LedgerEntryId of(String text) {
        return new LedgerEntryId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
