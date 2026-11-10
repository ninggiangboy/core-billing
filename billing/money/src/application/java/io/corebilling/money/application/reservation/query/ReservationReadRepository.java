package io.corebilling.money.application.reservation.query;

import io.corebilling.money.api.reservation.ReservationId;
import java.util.Optional;

/** Read-only projection of a reservation. */
public interface ReservationReadRepository {

    Optional<ReservationView> findById(ReservationId id);
}
