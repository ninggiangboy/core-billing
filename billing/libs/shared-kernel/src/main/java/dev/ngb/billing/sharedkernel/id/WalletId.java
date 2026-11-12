package dev.ngb.billing.sharedkernel.id;

import java.util.Objects;
import java.util.UUID;

/** Identifier of a Wallet. A value object so a raw UUID is never threaded through business code. */
public record WalletId(UUID value) {

    public WalletId {
        Objects.requireNonNull(value, "value");
    }

    public static WalletId of(String text) {
        return new WalletId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
