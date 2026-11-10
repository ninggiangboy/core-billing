package dev.ngb.core_billing.money.api.wallet;

import dev.ngb.core_billing.sharedkernel.WalletId;
import java.util.Optional;

/**
 * Read-only lookup for the contexts that charge or reserve against a wallet and must first know whose it is and
 * in which currency. It answers within the current tenant scope, so another tenant's wallet is simply absent.
 */
public interface WalletLookup {

    Optional<WalletSummary> find(WalletId id);
}
