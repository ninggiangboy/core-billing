package io.corebilling.platform.api.audit;

import io.corebilling.sharedkernel.TenantId;
import java.time.Instant;
import java.util.Objects;
import io.corebilling.platform.api.actor.RequestScope;

/** One traced administrative action; Platform-scope actions belong to the reserved platform tenant. */
public record AuditEntry(
        TenantId tenantId, String actor, String action, String target, String requestId, Instant occurredAt) {

    public AuditEntry {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static AuditEntry platform(String actor, String action, String target, String requestId, Instant at) {
        return new AuditEntry(RequestScope.PLATFORM_TENANT_ID, actor, action, target, requestId, at);
    }

    public static AuditEntry tenant(
            TenantId tenantId, String actor, String action, String target, String requestId, Instant at) {
        return new AuditEntry(tenantId, actor, action, target, requestId, at);
    }
}
