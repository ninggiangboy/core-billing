package dev.ngb.tenant.shared.error;

/** The request clashes with existing state, such as a duplicate email address. */
public class ConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConflictException(String message) {
        super(message);
    }
}
