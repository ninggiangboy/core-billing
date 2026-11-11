package dev.ngb.core_billing.platform.domain.tenantsystem;

import dev.ngb.core_billing.platform.api.tenant.TenantSystemId;
import dev.ngb.core_billing.sharedkernel.id.TenantId;
import java.time.Instant;
import java.util.Objects;

/** A third-party application integrated with one Tenant. It authenticates with API credentials. */
public final class TenantSystem {

    private final TenantSystemId id;
    private final TenantId tenantId;
    private final String name;
    private final Instant createdAt;
    private TenantSystemStatus status;

    private TenantSystem(TenantSystemId id, TenantId tenantId, String name, TenantSystemStatus status, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(name, "name");
        if (name.isBlank() || name.length() > 100) {
            throw new IllegalArgumentException("tenant system name must be 1-100 characters");
        }
        this.name = name.strip();
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static TenantSystem create(TenantSystemId id, TenantId tenantId, String name, Instant now) {
        return new TenantSystem(id, tenantId, name, TenantSystemStatus.ACTIVE, now);
    }

    public static TenantSystem restore(
            TenantSystemId id, TenantId tenantId, String name, TenantSystemStatus status, Instant createdAt) {
        return new TenantSystem(id, tenantId, name, status, createdAt);
    }

    public void disable() {
        status = TenantSystemStatus.DISABLED;
    }

    public boolean isActive() {
        return status == TenantSystemStatus.ACTIVE;
    }

    public TenantSystemId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public String name() {
        return name;
    }

    public TenantSystemStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
