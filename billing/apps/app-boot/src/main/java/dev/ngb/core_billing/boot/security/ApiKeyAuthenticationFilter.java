package dev.ngb.core_billing.boot.security;

import dev.ngb.core_billing.platform.api.actor.Actor;
import dev.ngb.core_billing.platform.application.auth.service.Authentication;
import dev.ngb.core_billing.platform.application.auth.exception.RateLimitExceededException;
import dev.ngb.core_billing.platform.domain.credential.ApiKey;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates {@code Authorization: Bearer cbk_...} requests from Tenant Systems. Anything else passes
 * through to the OIDC filter. A key that does not authenticate leaves the request anonymous, so the
 * caller gets the same 401 for a malformed, unknown, wrong, revoked or expired key.
 *
 * <p>Deliberately not a Spring bean: it is added to the security filter chain only, never to the
 * servlet container's own chain.
 */
class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final Authentication authentication;

    ApiKeyAuthenticationFilter(Authentication authentication) {
        this.authentication = authentication;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7) || !header.startsWith("Bearer " + ApiKey.PREFIX)) {
            chain.doFilter(request, response);
            return;
        }
        Optional<Actor> actor;
        try {
            actor = authentication.authenticateApiKey(header.substring("Bearer ".length()).strip());
        } catch (RateLimitExceededException limited) {
            response.setStatus(429);
            response.setHeader(HttpHeaders.RETRY_AFTER, "1");
            response.setContentType("application/json");
            response.getOutputStream().write("{\"error\":\"rate_limited\"}".getBytes(StandardCharsets.UTF_8));
            return;
        }
        actor.ifPresent(found -> {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new ActorAuthentication(found));
            SecurityContextHolder.setContext(context);
        });
        chain.doFilter(request, response);
    }
}
