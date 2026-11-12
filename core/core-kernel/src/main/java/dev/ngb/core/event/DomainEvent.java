package dev.ngb.core.event;

import dev.ngb.core.id.TenantId;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** A fact that happened inside a bounded context; delivered through the transactional outbox. */
public interface DomainEvent {

    /** Unique per event; consumers deduplicate on it. */
    UUID eventId();

    TenantId tenantId();

    /** Stable event name such as {@code charge.succeeded}. */
    String type();

    /** The aggregate the event belongs to, such as {@code wallet/<id>}. */
    String aggregate();

    Instant occurredAt();

    /**
     * The integration contract of the event: JSON-compatible values only (String, Number, Boolean,
     * nested Map and List). It is stored in the outbox exactly as given, so it never carries secrets.
     */
    Map<String, Object> payload();
}
