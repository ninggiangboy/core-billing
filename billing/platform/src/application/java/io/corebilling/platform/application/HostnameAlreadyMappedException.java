package io.corebilling.platform.application;

import io.corebilling.platform.api.ConflictException;
public class HostnameAlreadyMappedException extends ConflictException {

    private static final long serialVersionUID = 1L;

    public HostnameAlreadyMappedException(String hostname) {
        super("hostname already mapped: " + hostname);
    }
}
