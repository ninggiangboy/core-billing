package dev.ngb.core_billing.money.application.ledger.service;

import dev.ngb.core_billing.money.api.ledger.LedgerEntryId;
import dev.ngb.core_billing.money.api.ledger.LedgerPostingResult;
import dev.ngb.core_billing.money.domain.ledger.LedgerEntry;
import dev.ngb.core_billing.money.domain.wallet.Wallet;
import dev.ngb.core_billing.platform.api.error.IdempotencyConflictException;
import dev.ngb.core_billing.sharedkernel.IdGenerator;
import dev.ngb.core_billing.sharedkernel.Money;
import java.time.Instant;
import java.util.Optional;
import dev.ngb.core_billing.money.application.ledger.port.LedgerRepository;
import dev.ngb.core_billing.money.application.wallet.port.WalletRepository;

/**
 * Posts one signed amount to a locked wallet: assigns the sequence, writes the entry and the new projection
 * together, and replays an earlier identical posting instead of writing a second one.
 */
public final class LedgerPosting {

    private final WalletRepository wallets;
    private final LedgerRepository ledger;
    private final IdGenerator ids;

    public LedgerPosting(WalletRepository wallets, LedgerRepository ledger, IdGenerator ids) {
        this.wallets = wallets;
        this.ledger = ledger;
        this.ids = ids;
    }

    /** The earlier entry for this key, if the request is a retry of the same posting. */
    public Optional<LedgerPostingResult> replay(String idempotencyKey, Wallet wallet, Money signedAmount) {
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
    public record Posted(LedgerEntry entry, Money balanceAfter) {
        public LedgerPostingResult result() {
            return new LedgerPostingResult(entry.id(), entry.sequence(), entry.amount(), balanceAfter, false);
        }
    }

    public Posted credit(Wallet wallet, Money amount, LedgerEntry.Source source, LedgerEntry.Origin origin, Instant now) {
        long sequence = wallet.post(amount);
        LedgerEntry entry = LedgerEntry.credit(new LedgerEntryId(ids.newId()), wallet.tenantId(), wallet.id(), sequence, amount, source, origin, now);
        return write(wallet, entry);
    }

    public Posted debit(Wallet wallet, Money amount, LedgerEntry.Source source, LedgerEntry.Origin origin, Instant now) {
        long sequence = wallet.post(amount.negate());
        LedgerEntry entry = LedgerEntry.debit(new LedgerEntryId(ids.newId()), wallet.tenantId(), wallet.id(), sequence, amount, source, origin, now);
        return write(wallet, entry);
    }

    public Posted reversal(Wallet wallet, LedgerEntry original, LedgerEntry.Origin origin, Instant now) {
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
