package dev.ngb.billing.money.application.ledger.port;

import dev.ngb.billing.sharedkernel.id.WalletId;
import java.util.List;

/** Recomputes every wallet's projection from its ledger entries and reports where they disagree. */
public interface LedgerVerifier {

    /**
     * @param balance the wallet's stored balance
     * @param ledgerBalance the sum of its entries
     * @param lastSequence the wallet's stored last sequence
     * @param ledgerLastSequence the highest sequence among its entries
     * @param entryCount the number of entries (equal to the highest sequence when there are no gaps)
     */
    record Mismatch(WalletId walletId, long balance, long ledgerBalance, long lastSequence, long ledgerLastSequence, long entryCount) {}

    List<Mismatch> findMismatches();
}
