package dev.ngb.core_billing.sharedkernel;

import java.util.Objects;
import java.util.UUID;

/** Identifier of a Tenant. A value object so a raw UUID is never threaded through business code. */
public record TenantId(UUID value) {

    public TenantId {
        Objects.requireNonNull(value, "value");
    }

    public static TenantId of(String text) {
        return new TenantId(UUID.fromString(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
