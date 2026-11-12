package dev.ngb.billing.customer.domain.customer;

import dev.ngb.billing.platform.api.tenant.TenantSystemId;
import java.util.Objects;

/** A user account in a Tenant System, identified by that system's own id for it. */
public record ExternalIdentity(TenantSystemId tenantSystemId, String externalUserId) {

    public ExternalIdentity {
        Objects.requireNonNull(tenantSystemId, "tenantSystemId");
        Objects.requireNonNull(externalUserId, "externalUserId");
        if (externalUserId.isBlank() || externalUserId.length() > 255) {
            throw new IllegalArgumentException("externalUserId must be 1-255 characters");
        }
    }
}
