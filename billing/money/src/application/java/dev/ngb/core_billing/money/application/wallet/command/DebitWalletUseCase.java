package dev.ngb.core_billing.money.application.wallet.command;

import dev.ngb.core_billing.money.api.wallet.DebitCommand;
import dev.ngb.core_billing.money.api.wallet.DebitWallet;
import dev.ngb.core_billing.money.api.ledger.LedgerPostingResult;
import dev.ngb.core_billing.money.domain.ledger.LedgerEntry;
import dev.ngb.core_billing.money.domain.event.MoneyEvents;
import dev.ngb.core_billing.money.domain.wallet.Wallet;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.sharedkernel.Clock;
import dev.ngb.core_billing.sharedkernel.EventPublisher;
import dev.ngb.core_billing.sharedkernel.IdGenerator;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import java.time.Instant;
import dev.ngb.core_billing.money.application.ledger.command.LedgerPosting;
import dev.ngb.core_billing.money.application.ledger.port.LedgerRepository;
import dev.ngb.core_billing.money.application.wallet.port.WalletRepository;

/**
 * Takes money from a wallet outside a charge, once per idempotency key. A wallet is never driven below what
 * it can spend, so refunding a top-up is limited to the unspent balance.
 */
public class DebitWalletUseCase implements DebitWallet {

    private final UnitOfWork unitOfWork;
    private final WalletRepository wallets;
    private final EventPublisher events;
    private final LedgerPosting posting;
    private final IdGenerator ids;
    private final Clock clock;

    public DebitWalletUseCase(
            UnitOfWork unitOfWork, WalletRepository wallets, LedgerRepository ledger, EventPublisher events, IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.wallets = wallets;
        this.events = events;
        this.posting = new LedgerPosting(wallets, ledger, ids);
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public LedgerPostingResult debit(DebitCommand command) {
        RequestFingerprint.requireKey(command.idempotencyKey());
        if (!command.amount().isPositive()) {
            throw new IllegalArgumentException("a debit must be positive");
        }
        return unitOfWork.transactional(() -> {
            Wallet wallet = wallets.lockById(command.walletId()).orElseThrow(() -> new NotFoundException("wallet not found"));
            var replay = posting.replay(command.idempotencyKey(), wallet, command.amount().negate());
            if (replay.isPresent()) {
                return replay.get();
            }
            Instant now = clock.now();
            LedgerPosting.Posted posted = posting.debit(wallet, command.amount(),
                    LedgerEntry.Source.of(command.reference()),
                    new LedgerEntry.Origin(command.actor(), command.reason(), command.idempotencyKey()), now);
            events.publish(new MoneyEvents.WalletDebited(ids.newId(), posted.entry(), posted.balanceAfter(), now));
            return posted.result();
        });
    }
}
