package dev.ngb.core_billing.platform.api.actor;

import dev.ngb.core_billing.sharedkernel.TenantId;
import java.util.Objects;
import java.util.UUID;

/**
 * Where a request or job acts: on the Platform itself, or inside exactly one Tenant. It is built once
 * from the hostname (or the credential) and never from a client-supplied tenant id.
 */
public sealed interface RequestScope permits RequestScope.Platform, RequestScope.Tenant {

    /**
     * The Platform owns its own data (administrators, roles, audit entries) as a reserved tenant, so
     * platform-level rows use the same row-level-security template and composite foreign keys as every
     * other tenant-scoped row. The row is created by a migration.
     */
    TenantId PLATFORM_TENANT_ID = new TenantId(UUID.fromString("00000000-0000-7000-8000-000000000001"));

    /** The tenant the database sees for this scope. */
    TenantId dataTenantId();

    /** Platform Administration: sees only the Platform's own rows, never another tenant's. */
    record Platform() implements RequestScope {
        @Override
        public TenantId dataTenantId() {
            return PLATFORM_TENANT_ID;
        }
    }

    /** One Tenant: row-level security restricts every query to this tenant. */
    record Tenant(TenantId tenantId) implements RequestScope {
        public Tenant {
            Objects.requireNonNull(tenantId, "tenantId");
        }

        @Override
        public TenantId dataTenantId() {
            return tenantId;
        }
    }

    static RequestScope platform() {
        return new Platform();
    }

    static RequestScope tenant(TenantId tenantId) {
        return new Tenant(tenantId);
    }
}
