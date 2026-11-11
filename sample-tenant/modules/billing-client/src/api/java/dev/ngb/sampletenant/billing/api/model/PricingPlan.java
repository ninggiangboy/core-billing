package dev.ngb.sampletenant.billing.api.model;

import java.util.List;

/** A pricing plan with its immutable versions. */
public record PricingPlan(
        String id,
        String code,
        String name,
        String model,
        String currency,
        String status,
        java.time.Instant createdAt,
        java.time.Instant retiredAt,
        List<PlanVersion> versions) {}
