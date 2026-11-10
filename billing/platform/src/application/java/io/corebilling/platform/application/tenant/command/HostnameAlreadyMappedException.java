package io.corebilling.platform.application.tenant.command;

import io.corebilling.platform.api.error.ConflictException;
public class HostnameAlreadyMappedException extends ConflictException {

    private static final long serialVersionUID = 1L;

    public HostnameAlreadyMappedException(String hostname) {
        super("hostname already mapped: " + hostname);
    }
}
