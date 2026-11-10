package io.corebilling.money.application.wallet.command;

import io.corebilling.money.api.wallet.DebitCommand;
import io.corebilling.money.api.wallet.DebitWallet;
import io.corebilling.money.api.ledger.LedgerPostingResult;
import io.corebilling.money.domain.ledger.LedgerEntry;
import io.corebilling.money.domain.event.MoneyEvents;
import io.corebilling.money.domain.wallet.Wallet;
import io.corebilling.platform.api.NotFoundException;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.EventPublisher;
import io.corebilling.sharedkernel.IdGenerator;
import io.corebilling.sharedkernel.UnitOfWork;
import java.time.Instant;
import io.corebilling.money.application.ledger.command.LedgerPosting;
import io.corebilling.money.application.ledger.port.LedgerRepository;
import io.corebilling.money.application.wallet.port.WalletRepository;

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
