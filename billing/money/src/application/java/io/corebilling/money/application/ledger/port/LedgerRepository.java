package io.corebilling.money.application.ledger.port;

import io.corebilling.money.api.charge.ChargeId;
import io.corebilling.money.api.ledger.LedgerEntryId;
import io.corebilling.money.domain.ledger.LedgerEntry;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.WalletId;
import java.util.List;
import java.util.Optional;

/** The ledger can only be appended to and read. There is deliberately no update, delete or save. */
public interface LedgerRepository {

    void append(LedgerEntry entry);

    Optional<LedgerEntry> findById(LedgerEntryId id);

    Optional<LedgerEntry> findByIdempotencyKey(String idempotencyKey);

    Optional<LedgerEntry> findByCharge(ChargeId chargeId);

    /** The entry that reverses {@code original}, if one was posted. */
    Optional<LedgerEntry> findReversalOf(LedgerEntryId original);

    /** Entries with a sequence greater than {@code afterSequence}, in sequence order. */
    List<LedgerEntry> page(WalletId walletId, long afterSequence, int limit);

    /** The wallet's balance as of an entry: the sum of its entries up to and including {@code sequence}. */
    Money balanceThrough(WalletId walletId, long sequence);
}
