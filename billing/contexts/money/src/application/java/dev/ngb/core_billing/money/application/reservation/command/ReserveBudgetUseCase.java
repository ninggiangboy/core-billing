package dev.ngb.core_billing.money.application.reservation.command;

import dev.ngb.core_billing.money.api.reservation.ReservationId;
import dev.ngb.core_billing.money.api.reservation.ReservationResult;
import dev.ngb.core_billing.money.api.reservation.ReserveBudget;
import dev.ngb.core_billing.money.api.reservation.ReserveBudgetCommand;
import dev.ngb.core_billing.customer.api.lookup.CustomerLookup;
import dev.ngb.core_billing.money.domain.event.MoneyEvents;
import dev.ngb.core_billing.money.domain.reservation.Reservation;
import dev.ngb.core_billing.money.domain.wallet.Wallet;
import dev.ngb.core_billing.platform.api.idempotency.Fingerprint;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.sharedkernel.runtime.Clock;
import dev.ngb.core_billing.sharedkernel.event.EventPublisher;
import dev.ngb.core_billing.sharedkernel.runtime.IdGenerator;
import dev.ngb.core_billing.sharedkernel.runtime.UnitOfWork;
import java.time.Duration;
import java.time.Instant;
import dev.ngb.core_billing.money.application.reservation.port.ReservationRepository;

import dev.ngb.core_billing.money.application.wallet.port.WalletRepository;

/**
 * Checks that the wallet can cover the amount (or the Postpaid limit) and holds it for a bounded time, once per
 * idempotency key. Nothing is posted to the ledger until the reservation is captured.
 * <p>
 * The key is read after the wallet lock, which is what makes the read race-free under READ COMMITTED: a
 * concurrent duplicate waits on that lock and then sees the reservation the first request wrote. Do not move
 * the read above the lock.
 */
public class ReserveBudgetUseCase implements ReserveBudget {

    public static final Duration MIN_TTL = Duration.ofSeconds(1);
    public static final Duration MAX_TTL = Duration.ofDays(7);

    private final UnitOfWork unitOfWork;
    private final WalletRepository wallets;
    private final ReservationRepository reservations;
    private final EventPublisher events;
    private final CustomerLookup customers;
    private final IdGenerator ids;
    private final Clock clock;

    public ReserveBudgetUseCase(
            UnitOfWork unitOfWork, WalletRepository wallets, ReservationRepository reservations, EventPublisher events, CustomerLookup customers,
            IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.wallets = wallets;
        this.reservations = reservations;
        this.events = events;
        this.customers = customers;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public ReservationResult reserve(ReserveBudgetCommand command) {
        Fingerprint.requireKey(command.idempotencyKey());
        if (command.ttl().compareTo(MIN_TTL) < 0 || command.ttl().compareTo(MAX_TTL) > 0) {
            throw new IllegalArgumentException("a reservation must last between 1 second and 7 days");
        }
        String hash = Fingerprint.of(command.walletId(), command.amount(), command.ttl().toSeconds(), command.reference());
        return unitOfWork.transactional(() -> {
            Wallet wallet = wallets.lockById(command.walletId()).orElseThrow(() -> new NotFoundException("wallet not found"));
            requireVisible(wallet, command.caller());
            var existing = reservations.findByIdempotencyKey(command.idempotencyKey());
            if (existing.isPresent()) {
                Reservation reservation = existing.get();
                Fingerprint.requireSameRequest("reservation", command.idempotencyKey(), reservation.requestHash(), hash);
                return result(reservation, true);
            }
            Instant now = clock.now();
            Reservation reservation = Reservation.hold(
                    new ReservationId(ids.newId()), wallet, command.amount(), now.plus(command.ttl()), command.reference(),
                    command.idempotencyKey(), hash, now);
            wallets.update(wallet);
            reservations.add(reservation);
            events.publish(new MoneyEvents.ReservationCreated(ids.newId(), reservation, now));
            return result(reservation, false);
        });
    }

    static ReservationResult result(Reservation reservation, boolean replayed) {
        return new ReservationResult(reservation.id(), reservation.status(), reservation.amount(), reservation.expiresAt(), replayed);
    }

    private void requireVisible(Wallet wallet, java.util.UUID caller) {
        if (caller != null && customers.findFor(wallet.customerId(), caller).isEmpty()) {
            throw new NotFoundException("wallet not found");
        }
    }
}
