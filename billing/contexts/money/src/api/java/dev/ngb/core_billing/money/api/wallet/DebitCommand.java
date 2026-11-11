package dev.ngb.core_billing.money.api.wallet;

import dev.ngb.core_billing.sharedkernel.money.Money;
import dev.ngb.core_billing.sharedkernel.id.WalletId;
import java.util.Objects;

/** Take money from a wallet outside a charge, for example when a top-up is refunded. */
public record DebitCommand(WalletId walletId, Money amount, String reference, String reason, String idempotencyKey, String actor) {

    public DebitCommand {
        Objects.requireNonNull(walletId, "walletId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(actor, "actor");
    }
}
