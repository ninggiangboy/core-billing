package io.corebilling.money.api.reservation;

public interface ReleaseReservation {

    /**
     * Gives a held budget back. A reservation that is already released, expired or captured is reported as it is
     * (its {@code status}) and nothing changes; it is not an error.
     */
    ReservationResult release(ReservationId id);
}
