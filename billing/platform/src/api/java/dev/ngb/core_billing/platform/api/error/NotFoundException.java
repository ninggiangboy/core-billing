package dev.ngb.core_billing.platform.api.error;

/** The requested thing does not exist in the current scope. */
public class NotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NotFoundException(String message) {
        super(message);
    }
}
