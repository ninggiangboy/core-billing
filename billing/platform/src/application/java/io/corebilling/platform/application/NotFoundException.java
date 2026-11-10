package io.corebilling.platform.application;

/** The requested thing does not exist in the current scope. */
public class NotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NotFoundException(String message) {
        super(message);
    }
}
