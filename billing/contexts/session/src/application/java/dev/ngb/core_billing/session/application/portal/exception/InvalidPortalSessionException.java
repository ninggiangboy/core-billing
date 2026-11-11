package dev.ngb.core_billing.session.application.portal.exception;

/** The presented session token is unknown, already used, revoked or expired. The answer reveals which is not. */
public class InvalidPortalSessionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidPortalSessionException() {
        super("The session link is invalid or has expired.");
    }
}
