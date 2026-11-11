package dev.ngb.core_billing.boot.portal;

import java.time.Duration;
import org.springframework.http.ResponseCookie;

/**
 * The two cookies a Billing Session uses. The session cookie is HttpOnly, so scripts cannot read or copy it; the
 * CSRF cookie is readable on purpose, because the portal front end must echo it in a header. Both are host-only
 * (no Domain), scoped to {@code /portal}, and Secure unless a development deployment turns that off.
 */
public record PortalCookieSettings(boolean secure) {

    public static final String SESSION_COOKIE = "billing_portal";
    public static final String CSRF_COOKIE = "billing_portal_csrf";
    public static final String CSRF_HEADER = "X-Portal-CSRF";
    public static final String PATH = "/portal";

    public ResponseCookie session(String value, Duration maxAge) {
        return ResponseCookie.from(SESSION_COOKIE, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }

    public ResponseCookie csrf(String value, Duration maxAge) {
        return ResponseCookie.from(CSRF_COOKIE, value)
                .httpOnly(false)
                .secure(secure)
                .sameSite("Lax")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }

    public ResponseCookie clearSession() {
        return ResponseCookie.from(SESSION_COOKIE, "").httpOnly(true).secure(secure).sameSite("Lax").path(PATH).maxAge(Duration.ZERO).build();
    }

    public ResponseCookie clearCsrf() {
        return ResponseCookie.from(CSRF_COOKIE, "").httpOnly(false).secure(secure).sameSite("Lax").path(PATH).maxAge(Duration.ZERO).build();
    }
}
