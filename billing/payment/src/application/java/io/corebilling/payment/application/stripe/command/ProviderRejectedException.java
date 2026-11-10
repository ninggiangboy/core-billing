package io.corebilling.payment.application.stripe.command;

/** The payment provider refused a request for a reason that retrying will not change (bad key, invalid amount). The message is safe to show. */
public class ProviderRejectedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ProviderRejectedException(String message) {
        super(message);
    }
}
