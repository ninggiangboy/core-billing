package io.corebilling.sharedkernel;

import java.time.Instant;
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
}
