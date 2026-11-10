package dev.ngb.core_billing.pricing.application.plan.query;

import dev.ngb.core_billing.pricing.domain.plan.BillingCycle;
import dev.ngb.core_billing.pricing.domain.plan.RoundingPolicy;
import java.time.Instant;
import java.util.UUID;

/**
 * One plan version as a GET returns it. The rule stays as the stored JSON text; the controller turns it back into
 * the API's rule shape, so this read model never builds a domain price rule.
 */
public record PlanVersionView(
        UUID planId,
        int version,
        String rule,
        BillingCycle billingCycle,
        Long minCharge,
        Long maxCharge,
        RoundingPolicy rounding,
        Instant validFrom,
        Instant validUntil,
        Instant createdAt,
        String createdBy) {}
