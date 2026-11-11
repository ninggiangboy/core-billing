package dev.ngb.core_billing.money.application.wallet.query;

import dev.ngb.core_billing.money.api.wallet.WalletMode;
import dev.ngb.core_billing.sharedkernel.id.CustomerId;
import dev.ngb.core_billing.sharedkernel.money.Currency;
import dev.ngb.core_billing.sharedkernel.money.Money;
import dev.ngb.core_billing.sharedkernel.id.WalletId;
import java.time.Instant;

/** A wallet as the read API shows it; the derived balances are computed from the stored columns. */
public record WalletView(
        WalletId id,
        CustomerId customerId,
        Currency currency,
        WalletMode mode,
        Money creditLimit,
        Money balance,
        Money held,
        long lastSequence,
        Instant createdAt) {

    /** Balance minus holds. */
    public Money available() {
        return balance.minus(held);
    }

    /** The debt of a Postpaid wallet: how far the balance is below zero. Always zero for Prepaid. */
    public Money outstanding() {
        return mode == WalletMode.POSTPAID && balance.isNegative() ? balance.negate() : Money.zero(currency);
    }
}
