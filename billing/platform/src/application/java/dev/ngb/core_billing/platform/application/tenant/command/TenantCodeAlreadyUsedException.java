package dev.ngb.core_billing.platform.application.tenant.command;

import dev.ngb.core_billing.platform.api.error.ConflictException;
public class TenantCodeAlreadyUsedException extends ConflictException {

    private static final long serialVersionUID = 1L;

    public TenantCodeAlreadyUsedException(String code) {
        super("tenant code already used: " + code);
    }
}
