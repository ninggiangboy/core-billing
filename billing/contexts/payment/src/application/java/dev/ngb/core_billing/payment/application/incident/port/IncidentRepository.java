package dev.ngb.core_billing.payment.application.incident.port;

import dev.ngb.core_billing.payment.domain.incident.IncidentKind;
import dev.ngb.core_billing.payment.domain.stripe.StripeConfigurationId;
import dev.ngb.core_billing.sharedkernel.id.TenantId;
import java.time.Instant;
import java.util.UUID;

/** Findings a person should look at. Recording the same open finding again raises its count. */
public interface IncidentRepository {

    void record(UUID id, TenantId tenantId, StripeConfigurationId configurationId, IncidentKind kind, String subject, String detail, Instant now);

    /** @return false when the incident does not exist or was already resolved */
    boolean resolve(UUID id, Instant now);
}
