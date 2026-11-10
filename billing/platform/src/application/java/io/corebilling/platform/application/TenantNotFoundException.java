package io.corebilling.platform.application;

public class TenantNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TenantNotFoundException(String reference) {
        super("tenant not found: " + reference);
    }
}
