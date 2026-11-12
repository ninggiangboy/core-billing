package dev.ngb.billing.platform.application.tenant.query;

import dev.ngb.billing.platform.domain.tenant.TenantStatus;
import java.time.Instant;
import java.util.UUID;

/** A tenant as the platform admin GET handlers show it. */
public record TenantView(UUID id, String code, String name, TenantStatus status, Instant createdAt) {

    public boolean isActive() {
        return status == TenantStatus.ACTIVE;
    }
}
