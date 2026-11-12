package dev.ngb.billing.session.api.portal;

import java.util.Optional;

/**
 * The session operations the HTTP edge needs: turning the single-use exchange token into a portal cookie, and
 * turning a presented cookie back into a {@link PortalVisitor}. Tenant scoping comes from the request's
 * Tenant Context, so a token issued for one tenant is simply absent on another tenant's hostname.
 */
public interface PortalSessions {

    /** Empty when the cookie is unknown, revoked or expired. */
    Optional<PortalVisitor> resolve(String cookieToken);

    /** @throws IllegalStateException when the token is unknown, already used or expired */
    ExchangedSession exchange(String exchangeToken, String requestId);

    /** The cookie value, CSRF token and return URL are returned once, at exchange time; only digests are stored. */
    record ExchangedSession(PortalVisitor visitor, String cookieToken, String csrfToken, String returnUrl) {}
}
