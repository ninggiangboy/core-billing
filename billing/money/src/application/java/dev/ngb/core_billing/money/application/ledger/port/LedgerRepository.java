package dev.ngb.core_billing.money.application.ledger.port;

import dev.ngb.core_billing.money.api.charge.ChargeId;
import dev.ngb.core_billing.money.api.ledger.LedgerEntryId;
import dev.ngb.core_billing.money.domain.ledger.LedgerEntry;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.WalletId;
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
