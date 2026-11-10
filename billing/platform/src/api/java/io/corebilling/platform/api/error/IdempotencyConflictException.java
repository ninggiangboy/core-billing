package io.corebilling.platform.api.error;

/** The same idempotency key was reused for a different request. */
public class IdempotencyConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IdempotencyConflictException(String scope, String key) {
        super("idempotency key already used with a different request: " + scope + "/" + key);
    }
}
