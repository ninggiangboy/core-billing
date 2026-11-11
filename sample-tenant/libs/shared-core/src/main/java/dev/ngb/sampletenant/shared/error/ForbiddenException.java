package dev.ngb.sampletenant.shared.error;

/** The caller may not perform this operation. */
public class ForbiddenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ForbiddenException(String message) {
        super(message);
    }
}
