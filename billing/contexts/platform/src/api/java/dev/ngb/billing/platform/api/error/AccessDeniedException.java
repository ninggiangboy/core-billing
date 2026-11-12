package dev.ngb.billing.platform.api.error;

/** The actor is authenticated but may not do this in this scope. */
public class AccessDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AccessDeniedException(String message) {
        super(message);
    }
}
