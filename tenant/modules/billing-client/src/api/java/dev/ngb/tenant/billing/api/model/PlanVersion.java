package dev.ngb.tenant.billing.api.model;

import java.time.Instant;

/** One immutable version of a plan's terms. */
public record PlanVersion(
        int version,
        Rule rule,
        String billingCycle,
        Long minCharge,
        Long maxCharge,
        String rounding,
        Instant validFrom,
        Instant validUntil,
        Instant createdAt,
        String createdBy) {}
