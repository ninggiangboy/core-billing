package dev.ngb.core_billing.platform.domain.tenant;

import java.util.Objects;
import java.util.regex.Pattern;

/** The unique, URL-safe code of a Tenant; it is also the Tenant's subdomain label. */
public record TenantCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[a-z][a-z0-9-]{1,38}[a-z0-9]$");
    /** The code of the reserved platform tenant; no customer tenant may use it. */
    public static final String PLATFORM = "platform";

    public TenantCode {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "tenant code must be 3-40 lowercase letters, digits or hyphens, starting with a letter");
        }
    }

    /** The code for a tenant being created now: valid and not reserved. */
    public static TenantCode forNewTenant(String value) {
        TenantCode code = new TenantCode(value);
        if (PLATFORM.equals(code.value)) {
            throw new IllegalArgumentException("tenant code is reserved: " + value);
        }
        return code;
    }

    @Override
    public String toString() {
        return value;
    }
}
