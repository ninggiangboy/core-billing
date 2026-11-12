package dev.ngb.billing.payment.application.incident.query;

import dev.ngb.billing.payment.domain.incident.IncidentKind;
import java.time.Instant;
import java.util.UUID;

/** A finding a person should look at, as the GET handler shows it. */
public record IncidentView(
        UUID id, IncidentKind kind, String subject, String detail, int occurrences, Instant createdAt, Instant lastSeenAt, Instant resolvedAt) {}
