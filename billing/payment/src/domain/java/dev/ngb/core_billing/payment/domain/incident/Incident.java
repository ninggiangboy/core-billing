package dev.ngb.core_billing.payment.domain.incident;

import dev.ngb.core_billing.sharedkernel.TenantId;
import java.time.Instant;
import java.util.UUID;
import dev.ngb.core_billing.payment.domain.stripe.StripeConfigurationId;

/** Something a person should look at. The same open finding seen again raises {@code occurrences}. */
public record Incident(
        UUID id, TenantId tenantId, StripeConfigurationId configurationId, IncidentKind kind, String subject, String detail, int occurrences,
        Instant createdAt, Instant lastSeenAt, Instant resolvedAt) {}
