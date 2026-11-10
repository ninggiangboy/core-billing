package dev.ngb.core_billing.money.application.reservation.query;

import dev.ngb.core_billing.money.api.reservation.ReservationId;
import java.util.Optional;

/** Read-only projection of a reservation. */
public interface ReservationReadRepository {

    Optional<ReservationView> findById(ReservationId id);
}
