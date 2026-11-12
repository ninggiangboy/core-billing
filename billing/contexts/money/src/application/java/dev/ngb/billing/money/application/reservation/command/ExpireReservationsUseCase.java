package dev.ngb.billing.money.application.reservation.command;

import dev.ngb.core.event.EventPublisher;
import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.billing.money.api.reservation.ReservationId;
import dev.ngb.billing.money.application.reservation.port.ReservationRepository;
import dev.ngb.billing.money.application.wallet.port.WalletRepository;
import dev.ngb.billing.money.domain.event.MoneyEvents;
import dev.ngb.billing.money.domain.reservation.Reservation;
import dev.ngb.billing.money.domain.wallet.Wallet;
import java.time.Instant;
import java.util.List;

/**
 * Releases holds whose time has run out, in the current tenant. Safe to run on several instances at once: a
 * wallet that someone else has locked is skipped and picked up on the next run, and every flow locks the
 * wallet before the reservation, so this cannot deadlock with a capture.
 */
public class ExpireReservationsUseCase {

    private final UnitOfWork unitOfWork;
    private final WalletRepository wallets;
    private final ReservationRepository reservations;
    private final EventPublisher events;
    private final IdGenerator ids;
    private final Clock clock;

    public ExpireReservationsUseCase(
            UnitOfWork unitOfWork, WalletRepository wallets, ReservationRepository reservations, EventPublisher events, IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.wallets = wallets;
        this.reservations = reservations;
        this.events = events;
        this.ids = ids;
        this.clock = clock;
    }

    /** @return how many reservations were expired */
    public int execute(int batchSize) {
        List<ReservationId> candidates = unitOfWork.transactional(() -> reservations.findExpired(clock.now(), batchSize));
        int expired = 0;
        for (ReservationId id : candidates) {
            if (unitOfWork.transactional(() -> expireOne(id))) {
                expired++;
            }
        }
        return expired;
    }

    private boolean expireOne(ReservationId id) {
        Reservation seen = reservations.findById(id).orElse(null);
        if (seen == null) {
            return false;
        }
        Wallet wallet = wallets.tryLockById(seen.walletId()).orElse(null);
        if (wallet == null) {
            return false;
        }
        Reservation reservation = reservations.findById(id).orElse(null);
        Instant now = clock.now();
        if (reservation == null || !reservation.isActive() || !reservation.isExpired(now)) {
            return false;
        }
        reservation.expire(wallet, now);
        wallets.update(wallet);
        reservations.update(reservation);
        events.publish(new MoneyEvents.ReservationReleased(ids.newId(), reservation, true, now));
        return true;
    }
}
