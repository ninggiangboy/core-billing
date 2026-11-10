package io.corebilling.platform.application;

import io.corebilling.platform.api.ConflictException;
public class TenantCodeAlreadyUsedException extends ConflictException {

    private static final long serialVersionUID = 1L;

    public TenantCodeAlreadyUsedException(String code) {
        super("tenant code already used: " + code);
    }
}
