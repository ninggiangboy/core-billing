package dev.ngb.billing.boot.security;

import dev.ngb.billing.boot.portal.PortalCookieSettings;
import dev.ngb.billing.session.api.portal.PortalSessions;
import dev.ngb.billing.session.api.portal.PortalVisitor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates a Customer Portal request from its cookie, and enforces the double-submit CSRF token on every
 * state-changing portal call. The session is resolved inside the request's tenant scope, so a cookie issued for
 * one tenant is absent on another tenant's hostname. The exchange endpoint is public and has no cookie yet.
 */
class PortalSessionAuthenticationFilter extends OncePerRequestFilter {

    private static final String PORTAL_PREFIX = "/portal/";
    private static final String EXCHANGE_PATH = "/portal/api/session/exchange";
    private static final Set<String> SAFE_METHODS =
            Set.of(HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name(), HttpMethod.TRACE.name());

    private final PortalSessions sessions;
    private final PortalCookieSettings cookies;

    PortalSessionAuthenticationFilter(PortalSessions sessions, PortalCookieSettings cookies) {
        this.sessions = sessions;
        this.cookies = cookies;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith(PORTAL_PREFIX) || path.equals(EXCHANGE_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<String> token = cookie(request, PortalCookieSettings.SESSION_COOKIE);
        if (token.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        Optional<PortalVisitor> visitor = sessions.resolve(token.get());
        if (visitor.isEmpty()) {
            response.addHeader(HttpHeaders.SET_COOKIE, cookies.clearSession().toString());
            response.addHeader(HttpHeaders.SET_COOKIE, cookies.clearCsrf().toString());
            chain.doFilter(request, response);
            return;
        }
        if (!SAFE_METHODS.contains(request.getMethod()) && !csrfValid(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getOutputStream().write("{\"error\":\"csrf\"}".getBytes(StandardCharsets.UTF_8));
            return;
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new PortalSessionAuthentication(visitor.get()));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }

    private static boolean csrfValid(HttpServletRequest request) {
        Optional<String> cookie = cookie(request, PortalCookieSettings.CSRF_COOKIE);
        String header = request.getHeader(PortalCookieSettings.CSRF_HEADER);
        if (cookie.isEmpty() || header == null || header.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                cookie.get().getBytes(StandardCharsets.UTF_8), header.getBytes(StandardCharsets.UTF_8));
    }

    private static Optional<String> cookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies).filter(cookie -> name.equals(cookie.getName())).map(Cookie::getValue).findFirst();
    }
}
