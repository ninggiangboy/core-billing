package io.corebilling.money.application;

import io.corebilling.money.api.ReservationId;
import io.corebilling.money.domain.Reservation;
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
