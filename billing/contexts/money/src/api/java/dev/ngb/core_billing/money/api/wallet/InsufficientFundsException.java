package dev.ngb.core_billing.money.api.wallet;

/** The wallet cannot cover the amount: the available balance (or the postpaid limit) is too low. */
public class InsufficientFundsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InsufficientFundsException(String message) {
        super(message);
    }
}
