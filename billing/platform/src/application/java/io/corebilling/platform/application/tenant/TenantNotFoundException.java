package io.corebilling.platform.application.tenant;

import io.corebilling.platform.api.error.NotFoundException;
public class TenantNotFoundException extends NotFoundException {

    private static final long serialVersionUID = 1L;

    public TenantNotFoundException(String reference) {
        super("tenant not found: " + reference);
    }
}
