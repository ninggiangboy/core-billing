package io.corebilling.payment.application;

import io.corebilling.payment.domain.Incident;
import io.corebilling.payment.domain.IncidentKind;
import io.corebilling.payment.domain.StripeConfigurationId;
import io.corebilling.sharedkernel.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Findings a person should look at. Recording the same open finding again raises its count. */
public interface IncidentRepository {

    void record(UUID id, TenantId tenantId, StripeConfigurationId configurationId, IncidentKind kind, String subject, String detail, Instant now);

    List<Incident> list(boolean openOnly, int limit);

    /** @return false when the incident does not exist or was already resolved */
    boolean resolve(UUID id, Instant now);
}
