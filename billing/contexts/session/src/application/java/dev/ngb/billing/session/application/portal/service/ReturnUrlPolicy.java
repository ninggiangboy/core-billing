package dev.ngb.billing.session.application.portal.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Keeps a session's return URL on the Tenant's allowlist. An entry is an https origin with an optional path
 * prefix; a return URL matches it only when the scheme, host, effective port and path prefix agree, so a
 * crafted value cannot send the customer to another site.
 */
public final class ReturnUrlPolicy {

    private ReturnUrlPolicy() {}

    /** @throws IllegalArgumentException when the URL is not absolute https or no allowlist entry matches it */
    public static void requireAllowed(String returnUrl, Set<String> allowlist) {
        URI candidate = parse(returnUrl, "returnUrl");
        for (String entry : allowlist) {
            if (matches(parse(entry, "allowlist entry"), candidate)) {
                return;
            }
        }
        throw new IllegalArgumentException("the return URL is not on this tenant's allowlist");
    }

    /** @return the canonical form stored in the allowlist */
    public static String requireValidEntry(String entry) {
        URI uri = parse(entry, "return URL");
        if (uri.getRawQuery() != null) {
            throw new IllegalArgumentException("an allowlist entry is an origin with an optional path prefix, not a query");
        }
        return canonical(uri);
    }

    private static URI parse(String value, String name) {
        Objects.requireNonNull(value, name);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(name + " is not a valid URL");
        }
        if (!uri.isAbsolute() || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException(name + " must be an absolute https URL without user info or a fragment");
        }
        return uri;
    }

    private static boolean matches(URI entry, URI candidate) {
        if (!entry.getScheme().equalsIgnoreCase(candidate.getScheme())
                || !entry.getHost().equalsIgnoreCase(candidate.getHost())
                || effectivePort(entry) != effectivePort(candidate)) {
            return false;
        }
        String prefix = path(entry);
        String path = path(candidate);
        if (prefix.isEmpty()) {
            return true;
        }
        return prefix.endsWith("/") ? path.startsWith(prefix) : path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private static String path(URI uri) {
        String path = uri.getPath();
        return path == null ? "" : path;
    }

    private static int effectivePort(URI uri) {
        return uri.getPort() == -1 ? 443 : uri.getPort();
    }

    private static String canonical(URI uri) {
        StringBuilder value = new StringBuilder("https://").append(uri.getHost().toLowerCase(Locale.ROOT));
        if (uri.getPort() != -1 && uri.getPort() != 443) {
            value.append(':').append(uri.getPort());
        }
        value.append(path(uri));
        return value.toString();
    }
}
