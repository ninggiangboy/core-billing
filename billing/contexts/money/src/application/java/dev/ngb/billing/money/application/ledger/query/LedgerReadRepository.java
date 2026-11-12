package dev.ngb.billing.money.application.ledger.query;

import dev.ngb.billing.sharedkernel.id.WalletId;
import java.util.List;

/** Read-only projection of a wallet's ledger statement. */
public interface LedgerReadRepository {

    /** Entries after {@code afterSequence}, oldest first. */
    List<LedgerEntryView> page(WalletId walletId, long afterSequence, int limit);
}
