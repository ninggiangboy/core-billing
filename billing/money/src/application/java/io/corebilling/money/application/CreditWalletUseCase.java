package io.corebilling.money.application;

import io.corebilling.money.api.CreditCommand;
import io.corebilling.money.api.CreditWallet;
import io.corebilling.money.api.LedgerPostingResult;
import io.corebilling.money.domain.LedgerEntry;
import io.corebilling.money.domain.MoneyEvents;
import io.corebilling.money.domain.Wallet;
import io.corebilling.platform.api.NotFoundException;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.EventPublisher;
import io.corebilling.sharedkernel.IdGenerator;
import io.corebilling.sharedkernel.UnitOfWork;
import java.time.Instant;

/** Adds money to a wallet (a verified top-up, a refunded charge, an adjustment) once per idempotency key. */
public class CreditWalletUseCase implements CreditWallet {

    private final UnitOfWork unitOfWork;
    private final WalletRepository wallets;
    private final EventPublisher events;
    private final LedgerPosting posting;
    private final IdGenerator ids;
    private final Clock clock;

    public CreditWalletUseCase(
            UnitOfWork unitOfWork, WalletRepository wallets, LedgerRepository ledger, EventPublisher events, IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.wallets = wallets;
        this.events = events;
        this.posting = new LedgerPosting(wallets, ledger, ids);
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public LedgerPostingResult credit(CreditCommand command) {
        RequestFingerprint.requireKey(command.idempotencyKey());
        if (!command.amount().isPositive()) {
            throw new IllegalArgumentException("a credit must be positive");
        }
        return unitOfWork.transactional(() -> {
            Wallet wallet = wallets.lockById(command.walletId()).orElseThrow(() -> new NotFoundException("wallet not found"));
            var replay = posting.replay(command.idempotencyKey(), wallet, command.amount());
            if (replay.isPresent()) {
                return replay.get();
            }
            Instant now = clock.now();
            LedgerPosting.Posted posted = posting.credit(wallet, command.amount(),
                    LedgerEntry.Source.of(command.reference()),
                    new LedgerEntry.Origin(command.actor(), command.reason(), command.idempotencyKey()), now);
            events.publish(new MoneyEvents.WalletCredited(ids.newId(), posted.entry(), posted.balanceAfter(), now));
            return posted.result();
        });
    }
}
