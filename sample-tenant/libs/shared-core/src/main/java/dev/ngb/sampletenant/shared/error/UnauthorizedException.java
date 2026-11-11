package dev.ngb.sampletenant.shared.error;

/** The caller is not authenticated, or the session token is unknown or expired. */
public class UnauthorizedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnauthorizedException(String message) {
        super(message);
    }
}
