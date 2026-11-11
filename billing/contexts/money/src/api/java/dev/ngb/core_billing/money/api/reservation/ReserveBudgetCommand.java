package dev.ngb.core_billing.money.api.reservation;

import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.WalletId;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/** Hold {@code amount} on a wallet for at most {@code ttl}, before a resource is executed. */
public record ReserveBudgetCommand(WalletId walletId, Money amount, Duration ttl, String reference, String idempotencyKey, UUID caller) {

    public ReserveBudgetCommand(WalletId walletId, Money amount, Duration ttl, String reference, String idempotencyKey) {
        this(walletId, amount, ttl, reference, idempotencyKey, null);
    }

    public ReserveBudgetCommand {
        Objects.requireNonNull(walletId, "walletId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(ttl, "ttl");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    }
}
