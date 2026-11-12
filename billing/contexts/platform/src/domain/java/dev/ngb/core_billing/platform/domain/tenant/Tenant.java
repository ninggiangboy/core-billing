package dev.ngb.core_billing.platform.domain.tenant;

import dev.ngb.core.id.TenantId;
import java.time.Instant;
import java.util.Objects;

/** An independent business space on the Platform. */
public final class Tenant {

    private final TenantId id;
    private final TenantCode code;
    private final String name;
    private final Instant createdAt;
    private TenantStatus status;

    private Tenant(TenantId id, TenantCode code, String name, TenantStatus status, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.code = Objects.requireNonNull(code, "code");
        this.name = requireName(name);
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static Tenant create(TenantId id, TenantCode code, String name, Instant now) {
        return new Tenant(id, code, name, TenantStatus.ACTIVE, now);
    }

    /** Rebuilds a stored Tenant; persistence adapters use this, business code does not. */
    public static Tenant restore(
            TenantId id, TenantCode code, String name, TenantStatus status, Instant createdAt) {
        return new Tenant(id, code, name, status, createdAt);
    }

    /** Deactivating twice is allowed and changes nothing. */
    public void deactivate() {
        status = TenantStatus.DEACTIVATED;
    }

    public boolean isActive() {
        return status == TenantStatus.ACTIVE;
    }

    public TenantId id() {
        return id;
    }

    public TenantCode code() {
        return code;
    }

    public String name() {
        return name;
    }

    public TenantStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    private static String requireName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank() || name.length() > 200) {
            throw new IllegalArgumentException("tenant name must be 1-200 characters");
        }
        return name.strip();
    }
}
