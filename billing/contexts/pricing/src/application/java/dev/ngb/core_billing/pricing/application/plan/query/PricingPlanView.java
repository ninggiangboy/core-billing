package dev.ngb.core_billing.pricing.application.plan.query;

import dev.ngb.core_billing.pricing.domain.plan.PlanStatus;
import dev.ngb.core_billing.pricing.domain.rule.PricingModel;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A plan with its versions, shaped for the pricing admin GET handlers. */
public record PricingPlanView(
        UUID id,
        String code,
        String name,
        PricingModel model,
        String currency,
        PlanStatus status,
        Instant createdAt,
        Instant retiredAt,
        List<PlanVersionView> versions) {}
