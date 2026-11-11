package dev.ngb.core_billing.boot.web;

import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Strict CORS for the Customer Portal: only the exact origin the request arrived on (which is the tenant's own
 * hostname) may call it, with credentials. Every other origin gets no CORS permission at all.
 */
public class PortalCorsConfigurationSource implements CorsConfigurationSource {

    private static final String PORTAL_PREFIX = "/portal/";

    @Override
    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
        if (!request.getRequestURI().startsWith(PORTAL_PREFIX)) {
            return null;
        }
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null || !origin.equals(ownOrigin(request))) {
            CorsConfiguration denied = new CorsConfiguration();
            denied.setAllowedOrigins(List.of());
            return denied;
        }
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(origin));
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                HttpHeaders.CONTENT_TYPE, "X-Portal-CSRF", "Idempotency-Key", "X-Request-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(600L);
        return configuration;
    }

    private static String ownOrigin(HttpServletRequest request) {
        StringBuilder origin = new StringBuilder(request.getScheme()).append("://").append(request.getServerName());
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(request.getScheme()) && port == 80) || ("https".equals(request.getScheme()) && port == 443);
        if (!defaultPort) {
            origin.append(':').append(port);
        }
        return origin.toString();
    }
}
