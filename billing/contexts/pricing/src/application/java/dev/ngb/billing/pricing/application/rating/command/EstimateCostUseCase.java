package dev.ngb.billing.pricing.application.rating.command;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.billing.platform.api.error.ConflictException;
import dev.ngb.billing.platform.api.error.NotFoundException;
import dev.ngb.billing.pricing.application.plan.port.PlanRepository;
import dev.ngb.billing.pricing.application.rating.service.PlanSelection;
import dev.ngb.billing.pricing.domain.metric.Measure;
import dev.ngb.billing.pricing.domain.metric.UsageSummary;
import dev.ngb.billing.pricing.domain.plan.PlanStatus;
import dev.ngb.billing.pricing.domain.plan.PlanVersion;
import dev.ngb.billing.pricing.domain.plan.PricingPlan;
import dev.ngb.billing.pricing.domain.plan.PricingPlanId;
import dev.ngb.billing.pricing.domain.plan.Quote;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Prices expected usage without recording anything. It is a pure calculation from a plan version and
 * quantities, using the same rules and the same single rounding as the real charge, so an estimate and the
 * charge for identical input are the same amount.
 */
public class EstimateCostUseCase {

    /** {@code version} and {@code at} are optional: by default the version in force now. {@code usage} maps metric to the expected measure. */
    public record Request(PricingPlanId planId, Integer version, Instant at, Map<String, Measure> usage) {}

    public record Estimate(PricingPlan plan, PlanVersion version, Quote quote) {}

    private final UnitOfWork unitOfWork;
    private final PlanRepository plans;
    private final Clock clock;

    public EstimateCostUseCase(UnitOfWork unitOfWork, PlanRepository plans, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.plans = plans;
        this.clock = clock;
    }

    public Estimate estimate(Request request) {
        return unitOfWork.transactional(() -> {
            PricingPlan plan = plans.findById(request.planId()).orElseThrow(() -> new NotFoundException("pricing plan not found"));
            if (plan.status() == PlanStatus.RETIRED) {
                throw new ConflictException("the plan is retired");
            }
            PlanVersion version = PlanSelection.choose(plan, request.version(), request.at() == null ? clock.now() : request.at());
            return new Estimate(plan, version, version.quote(plan.currency(), summary(version, request.usage())));
        });
    }

    /** Rejects a metric the version does not price, because silently ignoring it would understate the estimate. */
    private static UsageSummary summary(PlanVersion version, Map<String, Measure> usage) {
        Map<String, String> priced = version.rule().metrics();
        Map<String, Measure> checked = new HashMap<>();
        for (Map.Entry<String, Measure> entry : usage.entrySet()) {
            if (!priced.containsKey(entry.getKey())) {
                throw new IllegalArgumentException("metric '" + entry.getKey() + "' is not priced by this plan");
            }
            checked.put(entry.getKey(), entry.getValue());
        }
        return new UsageSummary(checked);
    }
}
