package dev.ngb.core_billing.money.api.charge;

import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.WalletId;
import java.time.Instant;
import java.util.Objects;

/** What other contexts may know about a charge: who paid what, from where, and whether it went through. */
public record ChargeSummary(ChargeId id, WalletId walletId, Money amount, ChargeStatus status, ChargeSource source, String reference, Instant createdAt) {

    public ChargeSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(walletId, "walletId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
