package dev.ngb.core_billing.boot.portal;

import jakarta.servlet.http.HttpServletRequest;

/** Builds absolute URLs on the hostname the request arrived on, which is the tenant's own hostname. */
final class PortalUrls {

    private PortalUrls() {}

    static String origin(HttpServletRequest request) {
        StringBuilder origin = new StringBuilder(request.getScheme()).append("://").append(request.getServerName());
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(request.getScheme()) && port == 80) || ("https".equals(request.getScheme()) && port == 443);
        if (!defaultPort) {
            origin.append(':').append(port);
        }
        return origin.toString();
    }
}
