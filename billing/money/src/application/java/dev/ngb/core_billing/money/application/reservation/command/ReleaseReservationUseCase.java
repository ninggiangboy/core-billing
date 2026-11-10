package dev.ngb.core_billing.money.application.reservation.command;

import dev.ngb.core_billing.money.api.reservation.ReleaseReservation;
import dev.ngb.core_billing.money.api.reservation.ReservationId;
import dev.ngb.core_billing.money.api.reservation.ReservationResult;
import dev.ngb.core_billing.money.api.reservation.ReservationStatus;
import dev.ngb.core_billing.money.domain.event.MoneyEvents;
import dev.ngb.core_billing.money.domain.reservation.Reservation;
import dev.ngb.core_billing.money.domain.wallet.Wallet;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.sharedkernel.Clock;
import dev.ngb.core_billing.sharedkernel.EventPublisher;
import dev.ngb.core_billing.sharedkernel.IdGenerator;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import dev.ngb.core_billing.money.application.reservation.port.ReservationRepository;
import dev.ngb.core_billing.money.application.wallet.port.WalletRepository;

/** Gives a held budget back to the wallet. Releasing an already released reservation changes nothing. */
public class ReleaseReservationUseCase implements ReleaseReservation {

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

    /**
     * The contract other contexts use: gives the hold back if it is still active and otherwise reports the state
     * it is in (expired, captured) without failing, so a caller inside a larger transaction is never forced to
     * roll back over a hold that is already gone.
     */
    @Override
    public ReservationResult release(ReservationId id) {
        return release(id, true);
    }

    /** The administrator's release: a hold that is not active is a conflict. */
    public ReservationResult execute(ReservationId id) {
        return release(id, false);
    }

    private ReservationResult release(ReservationId id, boolean tolerateClosed) {
        return unitOfWork.transactional(() -> {
            Reservation unlocked = reservations.findById(id).orElseThrow(() -> new NotFoundException("reservation not found"));
            Wallet wallet = wallets.lockById(unlocked.walletId()).orElseThrow(() -> new NotFoundException("wallet not found"));
            Reservation reservation = reservations.findById(id).orElseThrow(() -> new NotFoundException("reservation not found"));
            if (reservation.status() == ReservationStatus.RELEASED) {
                return ReserveBudgetUseCase.result(reservation, true);
            }
            if (!reservation.isActive() && tolerateClosed) {
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
