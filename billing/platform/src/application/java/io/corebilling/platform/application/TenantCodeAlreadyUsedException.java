package io.corebilling.platform.application;

public class TenantCodeAlreadyUsedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TenantCodeAlreadyUsedException(String code) {
        super("tenant code already used: " + code);
    }
}
