package io.corebilling.payment.domain;

import io.corebilling.sharedkernel.TenantId;
import java.time.Instant;
import java.util.UUID;

/** Something a person should look at. The same open finding seen again raises {@code occurrences}. */
public record Incident(
        UUID id, TenantId tenantId, StripeConfigurationId configurationId, IncidentKind kind, String subject, String detail, int occurrences,
        Instant createdAt, Instant lastSeenAt, Instant resolvedAt) {}
