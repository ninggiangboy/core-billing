package io.corebilling.money.api;

public interface ReserveBudget {

    /** @throws InsufficientFundsException when the wallet cannot cover the hold */
    ReservationResult reserve(ReserveBudgetCommand command);
}
