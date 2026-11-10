package io.corebilling.platform.api.tenant;

import java.util.Objects;
import java.util.UUID;

/** Identifier of a Tenant System (a third-party application integrated with one Tenant). */
public record TenantSystemId(UUID value) {

    public TenantSystemId {
        Objects.requireNonNull(value, "value");
    }

    public static TenantSystemId of(String text) {
        return new TenantSystemId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
