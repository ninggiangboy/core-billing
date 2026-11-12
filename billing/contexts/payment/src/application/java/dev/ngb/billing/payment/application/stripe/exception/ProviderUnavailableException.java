package dev.ngb.billing.payment.application.stripe.exception;

/** The payment provider could not be reached or answered with a server error. The outcome of the call is unknown; retrying with the same idempotency key is safe. */
public class ProviderUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
