package io.corebilling.pricing.application;

import io.corebilling.pricing.domain.PlanId;
import io.corebilling.pricing.domain.PlanStatus;
import io.corebilling.pricing.domain.PlanVersion;
import io.corebilling.pricing.domain.PricingPlan;
import java.util.List;
import java.util.Optional;

/** Pricing plans of the current tenant, always loaded with their versions. */
public interface PlanRepository {

    /** Stores a new plan with its versions. */
    void add(PricingPlan plan);

    /** Stores a version that was just published; versions are never rewritten. */
    void addVersion(PlanVersion version);

    /** Writes the plan's own status (retirement). */
    void update(PricingPlan plan);

    Optional<PricingPlan> findById(PlanId id);

    /** Same as {@link #findById} but holds the plan's row until the transaction ends, so versions are numbered one at a time. */
    Optional<PricingPlan> lockById(PlanId id);

    Optional<PricingPlan> findByCode(String code);

    /** Newest first; {@code status} filters when given. */
    List<PricingPlan> list(PlanStatus status);
}
