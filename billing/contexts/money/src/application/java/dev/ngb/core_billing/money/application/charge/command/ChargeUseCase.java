package dev.ngb.core_billing.money.application.charge.command;

import dev.ngb.core_billing.money.api.charge.ChargeCommand;
import dev.ngb.core_billing.money.api.charge.ChargeResult;
import dev.ngb.core_billing.money.api.charge.ChargeWallet;
import dev.ngb.core_billing.money.api.ledger.LedgerEntryId;
import dev.ngb.core_billing.customer.api.lookup.CustomerLookup;
import dev.ngb.core_billing.money.domain.charge.Charge;
import dev.ngb.core_billing.money.domain.charge.ChargeService;
import dev.ngb.core_billing.money.domain.event.MoneyEvents;
import dev.ngb.core_billing.money.domain.reservation.Reservation;
import dev.ngb.core_billing.money.domain.wallet.Wallet;
import dev.ngb.core_billing.platform.api.idempotency.Fingerprint;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.sharedkernel.runtime.Clock;
import dev.ngb.core_billing.sharedkernel.event.EventPublisher;
import dev.ngb.core_billing.sharedkernel.runtime.IdGenerator;
import dev.ngb.core_billing.sharedkernel.runtime.UnitOfWork;
import dev.ngb.core_billing.sharedkernel.id.WalletId;
import java.time.Instant;
import java.util.UUID;
import dev.ngb.core_billing.money.application.charge.port.ChargeRepository;
import dev.ngb.core_billing.money.application.ledger.port.LedgerRepository;
import dev.ngb.core_billing.money.application.messaging.service.MoneyActors;
import dev.ngb.core_billing.money.application.reservation.port.ReservationRepository;

import dev.ngb.core_billing.money.application.wallet.port.WalletRepository;

/**
 * Records the actual cost incurred, from usage, from a reservation or on its own, once per idempotency key.
 * The charge, the ledger entry, the wallet projection, the reservation state and the outbox event commit in
 * one transaction. A wallet that cannot pay yields a recorded {@code insufficient_funds} failure, not an error.
 * <p>
 * The key is read after the wallet lock, which is what makes the read race-free under READ COMMITTED: a
 * concurrent duplicate waits on that lock and then sees the charge the first request wrote. Do not move the
 * read above the lock.
 */
public class ChargeUseCase implements ChargeWallet {

    private final UnitOfWork unitOfWork;
    private final WalletRepository wallets;
    private final ReservationRepository reservations;
    private final ChargeRepository charges;
    private final LedgerRepository ledger;
    private final EventPublisher events;
    private final ChargeService chargeService;
    private final CustomerLookup customers;
    private final IdGenerator ids;
    private final Clock clock;

    public ChargeUseCase(
            UnitOfWork unitOfWork,
            WalletRepository wallets,
            ReservationRepository reservations,
            ChargeRepository charges,
            LedgerRepository ledger,
            EventPublisher events,
            CustomerLookup customers,
            IdGenerator ids,
            Clock clock) {
        this.unitOfWork = unitOfWork;
        this.wallets = wallets;
        this.reservations = reservations;
        this.charges = charges;
        this.ledger = ledger;
        this.events = events;
        this.chargeService = new ChargeService(ids);
        this.customers = customers;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public ChargeResult charge(ChargeCommand command) {
        Fingerprint.requireKey(command.idempotencyKey());
        String hash = Fingerprint.of(command.walletId(), command.reservationId(), command.amount(), command.usageRef(), command.reference());
        return unitOfWork.transactional(() -> {
            WalletId walletId = walletOf(command);
            Wallet wallet = wallets.lockById(walletId).orElseThrow(() -> new NotFoundException("wallet not found"));
            requireVisible(wallet, command.caller());

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
                        command.idempotencyKey(), hash, MoneyActors.CHARGE, now));
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

    private void requireVisible(Wallet wallet, UUID caller) {
        if (caller != null && customers.findFor(wallet.customerId(), caller).isEmpty()) {
            throw new NotFoundException("wallet not found");
        }
    }

    private ChargeResult replay(Charge original, String hash, ChargeCommand command) {
        Fingerprint.requireSameRequest("charge", command.idempotencyKey(), original.requestHash(), hash);
        var entry = ledger.findByCharge(original.id());
        return result(original, entry.map(e -> e.id()).orElse(null), true);
    }

    private static ChargeResult result(Charge charge, LedgerEntryId entryId, boolean replayed) {
        return new ChargeResult(charge.id(), charge.status(), charge.failureReason(), charge.amount(), entryId, replayed);
    }
}
