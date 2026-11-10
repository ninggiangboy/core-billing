package io.corebilling.platform.application.auth.query;

public class RateLimitExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RateLimitExceededException() {
        super("rate limit exceeded");
    }
}
