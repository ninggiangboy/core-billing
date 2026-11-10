package io.corebilling.platform.application.tenant.command;

/** A token issued for one tenant was presented on another tenant's hostname. */
public class TenantMismatchException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TenantMismatchException() {
        super("the token belongs to a different tenant");
    }
}
