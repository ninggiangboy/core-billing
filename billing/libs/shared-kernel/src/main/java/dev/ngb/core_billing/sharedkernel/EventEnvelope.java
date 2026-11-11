package dev.ngb.core_billing.sharedkernel;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * An outbox row as the in-process consumers see it. The relay rebuilds it from the stored row, so a consumer
 * works from exactly what was committed with the business change and never from live state.
 */
public record EventEnvelope(UUID eventId, TenantId tenantId, String type, String aggregate, Instant occurredAt, Map<String, Object> payload)
        implements DomainEvent {

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(aggregate, "aggregate");
        Objects.requireNonNull(occurredAt, "occurredAt");
        payload = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(payload));
    }
}
