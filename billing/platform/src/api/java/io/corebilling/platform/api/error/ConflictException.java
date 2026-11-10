package io.corebilling.platform.api.error;

/** The request clashes with existing state (a duplicate name, a rotation already in progress). */
public class ConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConflictException(String message) {
        super(message);
    }
}
