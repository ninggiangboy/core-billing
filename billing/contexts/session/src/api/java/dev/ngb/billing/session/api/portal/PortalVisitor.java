package dev.ngb.billing.session.api.portal;

import dev.ngb.core.id.TenantId;
import dev.ngb.billing.sharedkernel.id.CustomerId;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The principal of a Customer Portal request: the tenant and the one customer the session was issued for, plus
 * the permissions it may use. It never carries a Tenant System id or a Billing User.
 */
public record PortalVisitor(TenantId tenantId, UUID sessionId, CustomerId customerId, Set<PortalPermission> permissions, Instant expiresAt) {

    public PortalVisitor {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(expiresAt, "expiresAt");
        permissions = Set.copyOf(permissions);
    }
}
