package dev.ngb.billing.money.application.reservation.query;

import dev.ngb.billing.money.api.reservation.ReservationId;
import java.util.Optional;

/** Read-only projection of a reservation. */
public interface ReservationReadRepository {

    Optional<ReservationView> findById(ReservationId id);
}
