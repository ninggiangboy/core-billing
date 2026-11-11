package dev.ngb.sampletenant.shared.error;

/** The request is malformed or violates a domain rule. */
public class BadRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BadRequestException(String message) {
        super(message);
    }
}
