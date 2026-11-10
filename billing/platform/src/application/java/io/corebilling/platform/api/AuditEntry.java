package io.corebilling.platform.api;

import io.corebilling.sharedkernel.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** One traced administrative action. A null tenant means the action happened at Platform scope. */
public record AuditEntry(
        TenantId tenantId, String actor, String action, String target, String requestId, Instant occurredAt) {

    public AuditEntry {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static AuditEntry platform(String actor, String action, String target, String requestId, Instant at) {
        return new AuditEntry(null, actor, action, target, requestId, at);
    }

    public static AuditEntry tenant(
            TenantId tenantId, String actor, String action, String target, String requestId, Instant at) {
        return new AuditEntry(Objects.requireNonNull(tenantId, "tenantId"), actor, action, target, requestId, at);
    }

    public Optional<TenantId> tenant() {
        return Optional.ofNullable(tenantId);
    }
}
