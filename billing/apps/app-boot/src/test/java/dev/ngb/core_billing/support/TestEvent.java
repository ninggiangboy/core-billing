package dev.ngb.core_billing.support;

import dev.ngb.core_billing.sharedkernel.event.DomainEvent;
import dev.ngb.core_billing.sharedkernel.id.TenantId;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TestEvent(UUID eventId, TenantId tenantId, String type, String aggregate, Instant occurredAt, Map<String, Object> payload)
        implements DomainEvent {

    public static TestEvent forTenant(TenantId tenantId) {
        UUID id = UUID.randomUUID();
        return new TestEvent(
                id, tenantId, "test.happened", "test/" + id, Instant.now(), Map.of("answer", 42, "note", "hello"));
    }
}
