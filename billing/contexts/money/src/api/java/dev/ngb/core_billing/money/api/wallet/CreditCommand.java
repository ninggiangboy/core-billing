package dev.ngb.core_billing.money.api.wallet;

import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.WalletId;
import java.util.Objects;

/** Add money to a wallet: a verified top-up, a refund of a charge, or an administrator's adjustment. */
public record CreditCommand(WalletId walletId, Money amount, String reference, String reason, String idempotencyKey, String actor) {

    public CreditCommand {
        Objects.requireNonNull(walletId, "walletId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(actor, "actor");
    }
}
