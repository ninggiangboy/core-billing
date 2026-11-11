package dev.ngb.core_billing.pricing.application.plan.service;

import dev.ngb.core_billing.pricing.api.plan.PeriodPrice;
import dev.ngb.core_billing.pricing.api.plan.PlanPricing;
import dev.ngb.core_billing.pricing.api.plan.SubscriptionTerms;
import dev.ngb.core_billing.pricing.application.plan.port.PlanRepository;
import dev.ngb.core_billing.pricing.domain.plan.PlanId;
import dev.ngb.core_billing.pricing.domain.plan.PlanStatus;
import dev.ngb.core_billing.pricing.domain.plan.PlanVersion;
import dev.ngb.core_billing.pricing.domain.plan.PricingPlan;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Answers the recurring-price questions of other contexts from the plan's versions; it writes nothing. */
public class PlanPricingService implements PlanPricing {

    private final UnitOfWork unitOfWork;
    private final PlanRepository plans;

    public PlanPricingService(UnitOfWork unitOfWork, PlanRepository plans) {
        this.unitOfWork = unitOfWork;
        this.plans = plans;
    }

    @Override
    public Optional<SubscriptionTerms> subscriptionTerms(UUID planId, Instant at) {
        return unitOfWork.transactional(() -> plans.findById(new PlanId(planId))
                .filter(plan -> plan.status() == PlanStatus.ACTIVE)
                .flatMap(plan -> plan.effectiveAt(at)
                        .filter(version -> version.recurringAmount(plan.currency()).isPresent())
                        .map(version -> new SubscriptionTerms(planId, plan.code(), plan.currency(), version.billingCycle()))));
    }

    @Override
    public Optional<PeriodPrice> periodPrice(UUID planId, Instant at) {
        return unitOfWork.transactional(() -> plans.findById(new PlanId(planId)).flatMap(plan -> price(plan, at)));
    }

    private static Optional<PeriodPrice> price(PricingPlan plan, Instant at) {
        Optional<PlanVersion> version = plan.effectiveAt(at);
        return version.flatMap(v -> v.recurringAmount(plan.currency())
                .map(amount -> new PeriodPrice(plan.id().value(), plan.code(), v.number(), v.billingCycle(), amount)));
    }
}
