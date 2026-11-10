package io.corebilling.money.application;

import io.corebilling.money.api.LedgerEntryId;
import io.corebilling.money.api.LedgerPostingResult;
import io.corebilling.money.domain.LedgerEntry;
import io.corebilling.money.domain.Wallet;
import io.corebilling.platform.api.IdempotencyConflictException;
import io.corebilling.sharedkernel.IdGenerator;
import io.corebilling.sharedkernel.Money;
import java.time.Instant;
import java.util.Optional;

/**
 * Posts one signed amount to a locked wallet: assigns the sequence, writes the entry and the new projection
 * together, and replays an earlier identical posting instead of writing a second one.
 */
final class LedgerPosting {

    private final WalletRepository wallets;
    private final LedgerRepository ledger;
    private final IdGenerator ids;

    LedgerPosting(WalletRepository wallets, LedgerRepository ledger, IdGenerator ids) {
        this.wallets = wallets;
        this.ledger = ledger;
        this.ids = ids;
    }

    /** The earlier entry for this key, if the request is a retry of the same posting. */
    Optional<LedgerPostingResult> replay(String idempotencyKey, Wallet wallet, Money signedAmount) {
        Optional<LedgerEntry> existing = ledger.findByIdempotencyKey(idempotencyKey);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        LedgerEntry entry = existing.get();
        if (!entry.walletId().equals(wallet.id()) || !entry.amount().equals(signedAmount)) {
            throw new IdempotencyConflictException("ledger", idempotencyKey);
        }
        return Optional.of(new LedgerPostingResult(
                entry.id(), entry.sequence(), entry.amount(), ledger.balanceThrough(entry.walletId(), entry.sequence()), true));
    }

    /** What a new posting produced, so the caller can announce it. */
    record Posted(LedgerEntry entry, Money balanceAfter) {
        LedgerPostingResult result() {
            return new LedgerPostingResult(entry.id(), entry.sequence(), entry.amount(), balanceAfter, false);
        }
    }

    Posted credit(Wallet wallet, Money amount, LedgerEntry.Source source, LedgerEntry.Origin origin, Instant now) {
        long sequence = wallet.post(amount);
        LedgerEntry entry = LedgerEntry.credit(new LedgerEntryId(ids.newId()), wallet.tenantId(), wallet.id(), sequence, amount, source, origin, now);
        return write(wallet, entry);
    }

    Posted debit(Wallet wallet, Money amount, LedgerEntry.Source source, LedgerEntry.Origin origin, Instant now) {
        long sequence = wallet.post(amount.negate());
        LedgerEntry entry = LedgerEntry.debit(new LedgerEntryId(ids.newId()), wallet.tenantId(), wallet.id(), sequence, amount, source, origin, now);
        return write(wallet, entry);
    }

    Posted reversal(Wallet wallet, LedgerEntry original, LedgerEntry.Origin origin, Instant now) {
        long sequence = wallet.post(original.amount().negate());
        LedgerEntry entry = LedgerEntry.reversalOf(original, new LedgerEntryId(ids.newId()), sequence, origin, now);
        return write(wallet, entry);
    }

    private Posted write(Wallet wallet, LedgerEntry entry) {
        ledger.append(entry);
        wallets.update(wallet);
        return new Posted(entry, wallet.balance());
    }
}
