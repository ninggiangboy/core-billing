package io.corebilling.platform.application;

/** The request clashes with existing state (a duplicate name, a rotation already in progress). */
public class ConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConflictException(String message) {
        super(message);
    }
}
