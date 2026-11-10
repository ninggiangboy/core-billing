package io.corebilling.money.application;

import io.corebilling.money.api.ReservationId;
import io.corebilling.money.api.ReservationResult;
import io.corebilling.money.api.ReservationStatus;
import io.corebilling.money.domain.MoneyEvents;
import io.corebilling.money.domain.Reservation;
import io.corebilling.money.domain.Wallet;
import io.corebilling.platform.api.ConflictException;
import io.corebilling.platform.api.NotFoundException;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.EventPublisher;
import io.corebilling.sharedkernel.IdGenerator;
import io.corebilling.sharedkernel.UnitOfWork;

/** Gives a held budget back to the wallet. Releasing an already released reservation changes nothing. */
public class ReleaseReservationUseCase {

    private final UnitOfWork unitOfWork;
    private final WalletRepository wallets;
    private final ReservationRepository reservations;
    private final EventPublisher events;
    private final IdGenerator ids;
    private final Clock clock;

    public ReleaseReservationUseCase(
            UnitOfWork unitOfWork, WalletRepository wallets, ReservationRepository reservations, EventPublisher events, IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.wallets = wallets;
        this.reservations = reservations;
        this.events = events;
        this.ids = ids;
        this.clock = clock;
    }

    public ReservationResult execute(ReservationId id) {
        return unitOfWork.transactional(() -> {
            Reservation unlocked = reservations.findById(id).orElseThrow(() -> new NotFoundException("reservation not found"));
            Wallet wallet = wallets.lockById(unlocked.walletId()).orElseThrow(() -> new NotFoundException("wallet not found"));
            Reservation reservation = reservations.findById(id).orElseThrow(() -> new NotFoundException("reservation not found"));
            if (reservation.status() == ReservationStatus.RELEASED) {
                return ReserveBudgetUseCase.result(reservation, true);
            }
            if (!reservation.isActive()) {
                throw new ConflictException("the reservation is already " + reservation.status().name().toLowerCase(java.util.Locale.ROOT));
            }
            var now = clock.now();
            reservation.release(wallet, now);
            wallets.update(wallet);
            reservations.update(reservation);
            events.publish(new MoneyEvents.ReservationReleased(ids.newId(), reservation, false, now));
            return ReserveBudgetUseCase.result(reservation, false);
        });
    }
}
