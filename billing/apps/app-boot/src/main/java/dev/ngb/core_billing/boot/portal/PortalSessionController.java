package dev.ngb.core_billing.boot.portal;

import dev.ngb.core_billing.platform.api.actor.PublicEndpoint;
import dev.ngb.core_billing.session.api.portal.CurrentPortalVisitor;
import dev.ngb.core_billing.session.api.portal.PortalPermission;
import dev.ngb.core_billing.session.api.portal.PortalSessions;
import dev.ngb.core_billing.session.api.portal.PortalVisitor;
import dev.ngb.core_billing.session.api.portal.RequiresPortalPermission;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The browser side of a Billing Session. The portal opens the URL the Tenant System returned, reads the token
 * from the URL fragment (so it never reaches a server log or a Referer header), and exchanges it here for the
 * HttpOnly session cookie and the readable CSRF cookie.
 */
@Profile("api")
@ConditionalOnProperty(name = "billing.portal.enabled", havingValue = "true", matchIfMissing = true)
@RestController
@RequestMapping("/portal/api/session")
public class PortalSessionController {

    public record ExchangeRequest(String token) {}

    public record SessionResponse(
            String sessionId, String customerId, List<String> permissions, Instant expiresAt) {
        static SessionResponse of(PortalVisitor visitor) {
            return new SessionResponse(
                    visitor.sessionId().toString(),
                    visitor.customerId().toString(),
                    visitor.permissions().stream().map(PortalPermission::code).sorted().toList(),
                    visitor.expiresAt());
        }
    }

    public record ExchangeResponse(SessionResponse session, String csrfToken, String returnUrl) {}

    private final PortalSessions sessions;
    private final CurrentPortalVisitor currentPortalVisitor;
    private final PortalCookieSettings cookies;

    public PortalSessionController(PortalSessions sessions, CurrentPortalVisitor currentPortalVisitor, PortalCookieSettings cookies) {
        this.sessions = sessions;
        this.currentPortalVisitor = currentPortalVisitor;
        this.cookies = cookies;
    }

    /** Public on purpose: the single-use token is the proof. A token for another tenant is unknown on this hostname. */
    @PostMapping("/exchange")
    @PublicEndpoint
    public ExchangeResponse exchange(
            @RequestBody ExchangeRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            HttpServletResponse response) {
        if (request.token() == null || request.token().isBlank()) {
            throw new IllegalArgumentException("token is required");
        }
        PortalSessions.ExchangedSession exchanged = sessions.exchange(request.token(), requestId);
        Duration maxAge = Duration.between(Instant.now(), exchanged.visitor().expiresAt());
        if (maxAge.isNegative() || maxAge.isZero()) {
            throw new IllegalArgumentException("the session has expired");
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookies.session(exchanged.cookieToken(), maxAge).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookies.csrf(exchanged.csrfToken(), maxAge).toString());
        return new ExchangeResponse(SessionResponse.of(exchanged.visitor()), exchanged.csrfToken(), exchanged.returnUrl());
    }

    /** What the portal may show, and how long the visit lasts. */
    @GetMapping
    @RequiresPortalPermission(PortalPermission.SESSION_VIEW)
    public SessionResponse session() {
        return SessionResponse.of(currentPortalVisitor.get());
    }
}
