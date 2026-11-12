package dev.ngb.billing.platform.application.tenant.exception;

import dev.ngb.billing.platform.api.error.NotFoundException;
public class TenantNotFoundException extends NotFoundException {

    private static final long serialVersionUID = 1L;

    public TenantNotFoundException(String reference) {
        super("tenant not found: " + reference);
    }
}
