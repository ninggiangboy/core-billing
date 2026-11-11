package dev.ngb.core_billing.pricing.application.rating.service;

import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.pricing.domain.plan.PlanVersion;
import dev.ngb.core_billing.pricing.domain.plan.PricingPlan;
import java.time.Instant;
import dev.ngb.core_billing.platform.api.error.NotFoundException;

/** Which version of a plan applies to a request. */
public final class PlanSelection {

    private PlanSelection() {}

    /** The named version, or the one in force at {@code at}. */
    public static PlanVersion choose(PricingPlan plan, Integer number, Instant at) {
        if (number != null) {
            return plan.version(number).orElseThrow(() -> new dev.ngb.core_billing.platform.api.error.NotFoundException("plan version not found"));
        }
        return plan.effectiveAt(at).orElseThrow(() -> new ConflictException("no version of plan '" + plan.code() + "' is in force at " + at));
    }
}
