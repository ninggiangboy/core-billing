package dev.ngb.billing.money.api.reservation;

public interface ReserveBudget {

    /** @throws InsufficientFundsException when the wallet cannot cover the hold */
    ReservationResult reserve(ReserveBudgetCommand command);
}
