package dev.ngb.core_billing.payment.application.stripe.command;

/** The webhook body does not match its signature, or the signature is stale. The message never contains a secret. */
public class InvalidSignatureException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InvalidSignatureException(String message) {
        super(message);
    }
}
