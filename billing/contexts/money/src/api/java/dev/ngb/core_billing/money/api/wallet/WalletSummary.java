package dev.ngb.core_billing.money.api.wallet;

import dev.ngb.core_billing.sharedkernel.money.Currency;
import dev.ngb.core_billing.sharedkernel.id.CustomerId;
import dev.ngb.core_billing.sharedkernel.id.WalletId;
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
