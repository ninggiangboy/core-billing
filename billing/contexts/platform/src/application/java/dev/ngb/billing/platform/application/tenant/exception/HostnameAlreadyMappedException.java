package dev.ngb.billing.platform.application.tenant.exception;

import dev.ngb.billing.platform.api.error.ConflictException;
public class HostnameAlreadyMappedException extends ConflictException {

    private static final long serialVersionUID = 1L;

    public HostnameAlreadyMappedException(String hostname) {
        super("hostname already mapped: " + hostname);
    }
}
