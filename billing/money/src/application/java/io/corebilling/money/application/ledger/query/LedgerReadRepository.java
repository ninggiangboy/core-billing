package io.corebilling.money.application.ledger.query;

import io.corebilling.sharedkernel.WalletId;
import java.util.List;

/** Read-only projection of a wallet's ledger statement. */
public interface LedgerReadRepository {

    /** Entries after {@code afterSequence}, oldest first. */
    List<LedgerEntryView> page(WalletId walletId, long afterSequence, int limit);
}
