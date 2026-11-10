package io.corebilling.payment.domain.incident;

import io.corebilling.sharedkernel.TenantId;
import java.time.Instant;
import java.util.UUID;
import io.corebilling.payment.domain.stripe.StripeConfigurationId;

/** Something a person should look at. The same open finding seen again raises {@code occurrences}. */
public record Incident(
        UUID id, TenantId tenantId, StripeConfigurationId configurationId, IncidentKind kind, String subject, String detail, int occurrences,
        Instant createdAt, Instant lastSeenAt, Instant resolvedAt) {}
