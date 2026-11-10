package io.corebilling.webhook.domain.endpoint;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * The syntax rules for a destination: an absolute https URL with a host, no credentials and no fragment.
 * Plain http is accepted only when the deployment says it is a development one. Which addresses a host may
 * resolve to is checked again at send time by {@link AddressPolicy}, because DNS can change.
 */
public final class WebhookUrl {

    public static final int MAX_LENGTH = 2048;

    private WebhookUrl() {}

    /** @return the URL in the form that is stored and called */
    public static String parse(String raw, boolean allowInsecure) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("the webhook url is required");
        }
        String text = raw.strip();
        if (text.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("the webhook url is too long");
        }
        URI uri;
        try {
            uri = new URI(text);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("the webhook url is not a valid URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean schemeOk = scheme.equals("https") || (allowInsecure && scheme.equals("http"));
        if (!schemeOk) {
            throw new IllegalArgumentException("the webhook url must use https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("the webhook url needs a host");
        }
        if (uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("the webhook url must not contain credentials");
        }
        if (uri.getRawFragment() != null) {
            throw new IllegalArgumentException("the webhook url must not contain a fragment");
        }
        if (uri.getPort() == 0 || uri.getPort() > 65535) {
            throw new IllegalArgumentException("the webhook url has an invalid port");
        }
        if (!allowInsecure && isAddressLiteral(uri.getHost()) && !publicLiteral(uri.getHost())) {
            throw new IllegalArgumentException("the webhook url must not point at a private or loopback address");
        }
        return uri.toString();
    }

    private static boolean isAddressLiteral(String host) {
        return host.startsWith("[") || host.chars().allMatch(c -> Character.isDigit(c) || c == '.');
    }

    private static boolean publicLiteral(String host) {
        try {
            // A literal never triggers a DNS lookup.
            return AddressPolicy.isPublic(InetAddress.getByName(host));
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
