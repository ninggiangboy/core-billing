package io.corebilling.payment.application.incident.port;

import io.corebilling.payment.domain.incident.IncidentKind;
import io.corebilling.payment.domain.stripe.StripeConfigurationId;
import io.corebilling.sharedkernel.TenantId;
import java.time.Instant;
import java.util.UUID;

/** Findings a person should look at. Recording the same open finding again raises its count. */
public interface IncidentRepository {

    void record(UUID id, TenantId tenantId, StripeConfigurationId configurationId, IncidentKind kind, String subject, String detail, Instant now);

    /** @return false when the incident does not exist or was already resolved */
    boolean resolve(UUID id, Instant now);
}
