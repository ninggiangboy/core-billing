package dev.ngb.billing.money.application.reservation.port;

import dev.ngb.billing.money.api.reservation.ReservationId;
import dev.ngb.billing.money.domain.reservation.Reservation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository {

    void add(Reservation reservation);

    void update(Reservation reservation);

    Optional<Reservation> findById(ReservationId id);

    Optional<Reservation> findByIdempotencyKey(String idempotencyKey);

    /** Ids of active reservations whose time has run out, oldest first. Takes no locks. */
    List<ReservationId> findExpired(Instant now, int limit);
}
