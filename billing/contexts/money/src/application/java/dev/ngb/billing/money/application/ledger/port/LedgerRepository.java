package dev.ngb.billing.money.application.ledger.port;

import dev.ngb.billing.money.api.charge.ChargeId;
import dev.ngb.billing.money.api.ledger.LedgerEntryId;
import dev.ngb.billing.money.domain.ledger.LedgerEntry;
import dev.ngb.billing.sharedkernel.money.Money;
import dev.ngb.billing.sharedkernel.id.WalletId;
import java.util.Optional;

/** The ledger can only be appended to and read. There is deliberately no update, delete or save. */
public interface LedgerRepository {

    void append(LedgerEntry entry);

    Optional<LedgerEntry> findById(LedgerEntryId id);

    Optional<LedgerEntry> findByIdempotencyKey(String idempotencyKey);

    Optional<LedgerEntry> findByCharge(ChargeId chargeId);

    /** The entry that reverses {@code original}, if one was posted. */
    Optional<LedgerEntry> findReversalOf(LedgerEntryId original);

    /** The wallet's balance as of an entry: the sum of its entries up to and including {@code sequence}. */
    Money balanceThrough(WalletId walletId, long sequence);
}
