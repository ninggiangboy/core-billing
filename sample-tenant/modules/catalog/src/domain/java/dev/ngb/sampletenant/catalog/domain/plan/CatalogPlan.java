package dev.ngb.sampletenant.catalog.domain.plan;

import java.time.Instant;
import java.util.UUID;

/** A local offer that names one Billing pricing plan. The Billing id is set by the bootstrap. */
public record CatalogPlan(
        UUID id,
        String code,
        String name,
        ResourceType resourceType,
        String description,
        String billingPlanId,
        boolean active,
        Instant createdAt) {

    public CatalogPlan withBillingPlan(String nextBillingPlanId) {
        return new CatalogPlan(id, code, name, resourceType, description, nextBillingPlanId, active, createdAt);
    }

    public boolean billable() {
        return active && billingPlanId != null && !billingPlanId.isBlank();
    }
}
