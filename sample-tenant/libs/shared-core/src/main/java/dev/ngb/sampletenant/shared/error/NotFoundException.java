package dev.ngb.sampletenant.shared.error;

/** The resource does not exist, or does not belong to the current account. */
public class NotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NotFoundException(String message) {
        super(message);
    }
}
