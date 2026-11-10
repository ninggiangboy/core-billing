package dev.ngb.core_billing.platform.application.tenant;

import dev.ngb.core_billing.platform.api.error.NotFoundException;
public class TenantNotFoundException extends NotFoundException {

    private static final long serialVersionUID = 1L;

    public TenantNotFoundException(String reference) {
        super("tenant not found: " + reference);
    }
}
