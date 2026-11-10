package io.corebilling.platform.application;

public class HostnameAlreadyMappedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public HostnameAlreadyMappedException(String hostname) {
        super("hostname already mapped: " + hostname);
    }
}
