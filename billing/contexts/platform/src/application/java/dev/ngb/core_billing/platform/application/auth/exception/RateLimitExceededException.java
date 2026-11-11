package dev.ngb.core_billing.platform.application.auth.exception;

public class RateLimitExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RateLimitExceededException() {
        super("rate limit exceeded");
    }
}
