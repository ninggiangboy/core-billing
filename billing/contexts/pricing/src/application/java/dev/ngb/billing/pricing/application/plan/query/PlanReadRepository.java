package dev.ngb.billing.pricing.application.plan.query;

import dev.ngb.billing.pricing.domain.plan.PlanStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only projections of plans and their versions. */
public interface PlanReadRepository {

    /** Newest first; {@code status} filters when given. */
    List<PricingPlanView> list(PlanStatus status);

    Optional<PricingPlanView> findById(UUID id);
}
