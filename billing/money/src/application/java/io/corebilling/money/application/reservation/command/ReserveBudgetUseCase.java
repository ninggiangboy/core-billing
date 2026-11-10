package io.corebilling.money.application.reservation.command;

import io.corebilling.money.api.reservation.ReservationId;
import io.corebilling.money.api.reservation.ReservationResult;
import io.corebilling.money.api.reservation.ReserveBudget;
import io.corebilling.money.api.reservation.ReserveBudgetCommand;
import io.corebilling.money.domain.event.MoneyEvents;
import io.corebilling.money.domain.reservation.Reservation;
import io.corebilling.money.domain.wallet.Wallet;
import io.corebilling.platform.api.error.IdempotencyConflictException;
import io.corebilling.platform.api.error.NotFoundException;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.EventPublisher;
import io.corebilling.sharedkernel.IdGenerator;
import io.corebilling.sharedkernel.UnitOfWork;
import java.time.Duration;
import java.time.Instant;
import io.corebilling.money.application.reservation.port.ReservationRepository;
import io.corebilling.money.application.wallet.command.RequestFingerprint;
import io.corebilling.money.application.wallet.port.WalletRepository;

/**
 * Checks that the wallet can cover the amount (or the Postpaid limit) and holds it for a bounded time, once per
 * idempotency key. Nothing is posted to the ledger until the reservation is captured.
 */
public class ReserveBudgetUseCase implements ReserveBudget {

    public static final Duration MIN_TTL = Duration.ofSeconds(1);
    public static final Duration MAX_TTL = Duration.ofDays(7);

    private final UnitOfWork unitOfWork;
    private final WalletRepository wallets;
    private final ReservationRepository reservations;
    private final EventPublisher events;
    private final IdGenerator ids;
    private final Clock clock;

    public ReserveBudgetUseCase(
            UnitOfWork unitOfWork, WalletRepository wallets, ReservationRepository reservations, EventPublisher events, IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.wallets = wallets;
        this.reservations = reservations;
        this.events = events;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public ReservationResult reserve(ReserveBudgetCommand command) {
        RequestFingerprint.requireKey(command.idempotencyKey());
        if (command.ttl().compareTo(MIN_TTL) < 0 || command.ttl().compareTo(MAX_TTL) > 0) {
            throw new IllegalArgumentException("a reservation must last between 1 second and 7 days");
        }
        String hash = RequestFingerprint.of(command.walletId(), command.amount(), command.ttl().toSeconds(), command.reference());
        return unitOfWork.transactional(() -> {
            Wallet wallet = wallets.lockById(command.walletId()).orElseThrow(() -> new NotFoundException("wallet not found"));
            var existing = reservations.findByIdempotencyKey(command.idempotencyKey());
            if (existing.isPresent()) {
                Reservation reservation = existing.get();
                if (!reservation.requestHash().equals(hash)) {
                    throw new IdempotencyConflictException("reservation", command.idempotencyKey());
                }
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
}
