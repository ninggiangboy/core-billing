package io.corebilling.money.api.wallet;

import io.corebilling.sharedkernel.Currency;
import io.corebilling.sharedkernel.CustomerId;
import io.corebilling.sharedkernel.WalletId;
import java.util.Objects;

/** What other contexts may know about a wallet. Balances are deliberately not part of it. */
public record WalletSummary(WalletId id, CustomerId customerId, Currency currency, WalletMode mode) {

    public WalletSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(mode, "mode");
    }
}
