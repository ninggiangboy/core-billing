package dev.ngb.core_billing.platform.domain.tenant;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** A lower-case DNS hostname without port. */
public record Hostname(String value) {

    private static final Pattern LABEL = Pattern.compile("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$");

    public Hostname {
        Objects.requireNonNull(value, "value");
        if (value.length() > 253 || value.isEmpty()) {
            throw new IllegalArgumentException("invalid hostname length");
        }
        for (String label : value.split("\\.", -1)) {
            if (!LABEL.matcher(label).matches()) {
                throw new IllegalArgumentException("invalid hostname: " + value);
            }
        }
    }

    /** Parses a Host header value: drops a port and lower-cases. */
    public static Hostname fromHostHeader(String hostHeader) {
        Objects.requireNonNull(hostHeader, "hostHeader");
        String host = hostHeader.strip().toLowerCase(Locale.ROOT);
        int colon = host.lastIndexOf(':');
        if (colon >= 0) {
            host = host.substring(0, colon);
        }
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        return new Hostname(host);
    }

    @Override
    public String toString() {
        return value;
    }
}
