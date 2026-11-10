package dev.ngb.core_billing.money.application.charge.command;

import dev.ngb.core_billing.money.api.charge.ChargeCommand;
import dev.ngb.core_billing.money.api.charge.ChargeResult;
import dev.ngb.core_billing.money.api.charge.ChargeWallet;
import dev.ngb.core_billing.money.api.ledger.LedgerEntryId;
import dev.ngb.core_billing.money.domain.charge.Charge;
import dev.ngb.core_billing.money.domain.charge.ChargeService;
import dev.ngb.core_billing.money.domain.event.MoneyEvents;
import dev.ngb.core_billing.money.domain.reservation.Reservation;
import dev.ngb.core_billing.money.domain.wallet.Wallet;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.platform.api.error.IdempotencyConflictException;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.sharedkernel.Clock;
import dev.ngb.core_billing.sharedkernel.EventPublisher;
import dev.ngb.core_billing.sharedkernel.IdGenerator;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import dev.ngb.core_billing.sharedkernel.WalletId;
import java.time.Instant;
import dev.ngb.core_billing.money.application.charge.port.ChargeRepository;
import dev.ngb.core_billing.money.application.ledger.port.LedgerRepository;
import dev.ngb.core_billing.money.application.reservation.port.ReservationRepository;
import dev.ngb.core_billing.money.application.wallet.command.RequestFingerprint;
import dev.ngb.core_billing.money.application.wallet.port.WalletRepository;

/**
 * Records the actual cost incurred, from usage, from a reservation or on its own, once per idempotency key.
 * The charge, the ledger entry, the wallet projection, the reservation state and the outbox event commit in
 * one transaction. A wallet that cannot pay yields a recorded {@code insufficient_funds} failure, not an error.
 */
public class ChargeUseCase implements ChargeWallet {

    /** The actor recorded on the ledger entry: a charge is raised by the system on a caller's behalf. */
    private static final String ACTOR = "system/charge";

    private final UnitOfWork unitOfWork;
    private final WalletRepository wallets;
    private final ReservationRepository reservations;
    private final ChargeRepository charges;
    private final LedgerRepository ledger;
    private final EventPublisher events;
    private final ChargeService chargeService;
    private final IdGenerator ids;
    private final Clock clock;

    public ChargeUseCase(
            UnitOfWork unitOfWork,
            WalletRepository wallets,
            ReservationRepository reservations,
            ChargeRepository charges,
            LedgerRepository ledger,
            EventPublisher events,
            IdGenerator ids,
            Clock clock) {
        this.unitOfWork = unitOfWork;
        this.wallets = wallets;
        this.reservations = reservations;
        this.charges = charges;
        this.ledger = ledger;
        this.events = events;
        this.chargeService = new ChargeService(ids);
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public ChargeResult charge(ChargeCommand command) {
        RequestFingerprint.requireKey(command.idempotencyKey());
        String hash = RequestFingerprint.of(command.walletId(), command.reservationId(), command.amount(), command.usageRef(), command.reference());
        return unitOfWork.transactional(() -> {
            WalletId walletId = walletOf(command);
            Wallet wallet = wallets.lockById(walletId).orElseThrow(() -> new NotFoundException("wallet not found"));

            var existing = charges.findByIdempotencyKey(command.idempotencyKey());
            if (existing.isPresent()) {
                return replay(existing.get(), hash, command);
            }

            Reservation reservation = null;
            if (command.reservationId() != null) {
                reservation = reservations.findById(command.reservationId()).orElseThrow(() -> new NotFoundException("reservation not found"));
            }
            Instant now = clock.now();
            ChargeService.Outcome outcome;
            try {
                outcome = chargeService.charge(new ChargeService.Request(
                        wallet, reservation, command.amount(), command.source(), command.usageRef(), command.reference(),
                        command.idempotencyKey(), hash, ACTOR, now));
            } catch (IllegalStateException reservationNotUsable) {
                throw new ConflictException(reservationNotUsable.getMessage());
            }

            charges.add(outcome.charge());
            if (outcome.entry() != null) {
                ledger.append(outcome.entry());
                wallets.update(wallet);
                if (reservation != null) {
                    reservations.update(reservation);
                }
                events.publish(new MoneyEvents.ChargeSucceeded(ids.newId(), outcome.charge(), outcome.entry(), now));
            } else {
                events.publish(new MoneyEvents.ChargeFailed(ids.newId(), outcome.charge(), now));
            }
            return result(outcome.charge(), outcome.entry() == null ? null : outcome.entry().id(), false);
        });
    }

    private WalletId walletOf(ChargeCommand command) {
        if (command.reservationId() == null) {
            return command.walletId();
        }
        Reservation reservation = reservations.findById(command.reservationId()).orElseThrow(() -> new NotFoundException("reservation not found"));
        if (command.walletId() != null && !command.walletId().equals(reservation.walletId())) {
            throw new IllegalArgumentException("the reservation belongs to another wallet");
        }
        return reservation.walletId();
    }

    private ChargeResult replay(Charge original, String hash, ChargeCommand command) {
        if (!original.requestHash().equals(hash)) {
            throw new IdempotencyConflictException("charge", command.idempotencyKey());
        }
        var entry = ledger.findByCharge(original.id());
        return result(original, entry.map(e -> e.id()).orElse(null), true);
    }

    private static ChargeResult result(Charge charge, LedgerEntryId entryId, boolean replayed) {
        return new ChargeResult(charge.id(), charge.status(), charge.failureReason(), charge.amount(), entryId, replayed);
    }
}
