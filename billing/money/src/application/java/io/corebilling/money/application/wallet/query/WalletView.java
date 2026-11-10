package io.corebilling.money.application.wallet.query;

import io.corebilling.money.api.wallet.WalletMode;
import io.corebilling.sharedkernel.CustomerId;
import io.corebilling.sharedkernel.Currency;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.WalletId;
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
