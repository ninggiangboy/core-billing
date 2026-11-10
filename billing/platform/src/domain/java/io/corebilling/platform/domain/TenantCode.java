package io.corebilling.platform.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/** The unique, URL-safe code of a Tenant; it is also the Tenant's subdomain label. */
public record TenantCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[a-z][a-z0-9-]{1,38}[a-z0-9]$");
    private static final String RESERVED = "platform";

    public TenantCode {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "tenant code must be 3-40 lowercase letters, digits or hyphens, starting with a letter");
        }
        if (RESERVED.equals(value)) {
            throw new IllegalArgumentException("tenant code is reserved: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
