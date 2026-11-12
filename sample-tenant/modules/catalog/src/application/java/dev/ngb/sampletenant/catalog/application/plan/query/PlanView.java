package dev.ngb.sampletenant.catalog.application.plan.query;

import dev.ngb.sampletenant.catalog.domain.plan.ResourceType;
import java.time.Instant;
import java.util.UUID;

/** A catalog plan as the tenant's screens render it. */
public record PlanView(
        UUID id,
        String code,
        String name,
        ResourceType resourceType,
        String description,
        String billingPlanId,
        boolean active,
        Instant createdAt) {

    public boolean billable() {
        return active && billingPlanId != null && !billingPlanId.isBlank();
    }
}
