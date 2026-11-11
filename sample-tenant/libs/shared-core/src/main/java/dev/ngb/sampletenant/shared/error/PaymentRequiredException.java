package dev.ngb.sampletenant.shared.error;

/** The wallet cannot cover the requested amount. */
public class PaymentRequiredException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PaymentRequiredException(String message) {
        super(message);
    }
}
