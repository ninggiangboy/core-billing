package io.corebilling.pricing.application.plan.query;

import io.corebilling.platform.api.error.NotFoundException;
import io.corebilling.pricing.domain.plan.PlanStatus;
import io.corebilling.sharedkernel.UnitOfWork;
import java.util.List;
import java.util.UUID;

/** Reads plans for the admin GET handlers; the write side is not involved. */
public class PlanQueries {

    private final UnitOfWork unitOfWork;
    private final PlanReadRepository plans;

    public PlanQueries(UnitOfWork unitOfWork, PlanReadRepository plans) {
        this.unitOfWork = unitOfWork;
        this.plans = plans;
    }

    /** Newest first; {@code status} filters when given. */
    public List<PricingPlanView> list(PlanStatus status) {
        return unitOfWork.transactional(() -> plans.list(status));
    }

    public PricingPlanView get(UUID id) {
        return unitOfWork.transactional(() -> plans.findById(id).orElseThrow(() -> new NotFoundException("pricing plan not found")));
    }
}
