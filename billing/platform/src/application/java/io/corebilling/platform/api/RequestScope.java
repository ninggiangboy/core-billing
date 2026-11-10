package io.corebilling.platform.api;

import io.corebilling.sharedkernel.TenantId;
import java.util.Objects;

/**
 * Where a request or job acts: on the Platform itself, or inside exactly one Tenant. It is built once
 * from the hostname (or the credential) and never from a client-supplied tenant id.
 */
public sealed interface RequestScope permits RequestScope.Platform, RequestScope.Tenant {

    /** Platform Administration: may touch only platform-owned tables. */
    record Platform() implements RequestScope {}

    /** One Tenant: row-level security restricts every query to this tenant. */
    record Tenant(TenantId tenantId) implements RequestScope {
        public Tenant {
            Objects.requireNonNull(tenantId, "tenantId");
        }
    }

    static RequestScope platform() {
        return new Platform();
    }

    static RequestScope tenant(TenantId tenantId) {
        return new Tenant(tenantId);
    }
}
