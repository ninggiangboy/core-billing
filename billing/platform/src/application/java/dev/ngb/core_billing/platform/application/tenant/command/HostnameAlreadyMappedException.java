package dev.ngb.core_billing.platform.application.tenant.command;

import dev.ngb.core_billing.platform.api.error.ConflictException;
public class HostnameAlreadyMappedException extends ConflictException {

    private static final long serialVersionUID = 1L;

    public HostnameAlreadyMappedException(String hostname) {
        super("hostname already mapped: " + hostname);
    }
}
