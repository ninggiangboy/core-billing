package io.corebilling.money.application.ledger.command;

import io.corebilling.money.api.ledger.EntryType;
import io.corebilling.money.api.ledger.LedgerEntryId;
import io.corebilling.money.domain.ledger.LedgerEntry;
import io.corebilling.money.domain.event.MoneyEvents;
import io.corebilling.money.domain.wallet.Wallet;
import io.corebilling.platform.api.ConflictException;
import io.corebilling.platform.api.NotFoundException;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.EventPublisher;
import io.corebilling.sharedkernel.IdGenerator;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.UnitOfWork;
import java.time.Instant;
import java.util.Optional;
import io.corebilling.money.application.ledger.port.LedgerRepository;
import io.corebilling.money.application.wallet.port.WalletRepository;

/**
 * The only way to correct the ledger: post a reversal that negates an earlier entry on the same wallet, then
 * optionally a replacement entry with the right amount. The original is never touched. Reversing the same entry
 * twice returns the first reversal.
 */
public class ReverseEntryUseCase {

    /** An optional corrected entry: a DEBIT or CREDIT of a positive {@code amount} in the wallet's currency. */
    public record Replacement(EntryType type, Money amount, String reference) {}

    public record Command(LedgerEntryId entryId, String reason, Replacement replacement, String actor) {}

    public record Result(LedgerEntry reversal, LedgerEntry replacement, boolean replayed) {}

    private final UnitOfWork unitOfWork;
    private final WalletRepository wallets;
    private final LedgerRepository ledger;
    private final EventPublisher events;
    private final LedgerPosting posting;
    private final IdGenerator ids;
    private final Clock clock;

    public ReverseEntryUseCase(
            UnitOfWork unitOfWork, WalletRepository wallets, LedgerRepository ledger, EventPublisher events, IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.wallets = wallets;
        this.ledger = ledger;
        this.events = events;
        this.posting = new LedgerPosting(wallets, ledger, ids);
        this.ids = ids;
        this.clock = clock;
    }

    public Result execute(Command command) {
        if (command.reason() == null || command.reason().isBlank()) {
            throw new IllegalArgumentException("a reversal needs a reason");
        }
        return unitOfWork.transactional(() -> {
            LedgerEntry original = ledger.findById(command.entryId()).orElseThrow(() -> new NotFoundException("ledger entry not found"));
            Wallet wallet = wallets.lockById(original.walletId()).orElseThrow(() -> new NotFoundException("wallet not found"));

            Optional<LedgerEntry> earlier = ledger.findReversalOf(original.id());
            if (earlier.isPresent()) {
                return new Result(earlier.get(), ledger.findByIdempotencyKey("replacement:" + original.id()).orElse(null), true);
            }
            if (original.type() == EntryType.REVERSAL) {
                throw new ConflictException("a reversal cannot itself be reversed; post a new entry instead");
            }
            Instant now = clock.now();
            LedgerPosting.Posted reversal = posting.reversal(wallet, original,
                    new LedgerEntry.Origin(command.actor(), command.reason(), "reversal:" + original.id()), now);
            events.publish(new MoneyEvents.EntryReversed(ids.newId(), reversal.entry(), original, now));

            LedgerEntry replacement = null;
            if (command.replacement() != null) {
                replacement = postReplacement(wallet, original, command, now);
            }
            return new Result(reversal.entry(), replacement, false);
        });
    }

    private LedgerEntry postReplacement(Wallet wallet, LedgerEntry original, Command command, Instant now) {
        Replacement replacement = command.replacement();
        if (!replacement.amount().isPositive()) {
            throw new IllegalArgumentException("the replacement amount must be positive");
        }
        LedgerEntry.Source source = LedgerEntry.Source.of(replacement.reference() == null ? "replacement:" + original.id() : replacement.reference());
        LedgerEntry.Origin origin = new LedgerEntry.Origin(command.actor(), command.reason(), "replacement:" + original.id());
        return switch (replacement.type()) {
            case DEBIT -> posting.debit(wallet, replacement.amount(), source, origin, now).entry();
            case CREDIT -> posting.credit(wallet, replacement.amount(), source, origin, now).entry();
            case REVERSAL -> throw new IllegalArgumentException("a replacement is a debit or a credit");
        };
    }
}
