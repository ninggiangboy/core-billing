package dev.ngb.sampletenant.shared.error;

/** Billing could not be reached, or answered with an error this system cannot handle. */
public class UpstreamUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UpstreamUnavailableException(String message) {
        super(message);
    }

    public UpstreamUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
