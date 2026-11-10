package io.corebilling.pricing.application.rating.command;

import io.corebilling.platform.api.error.ConflictException;
import io.corebilling.pricing.domain.plan.PlanVersion;
import io.corebilling.pricing.domain.plan.PricingPlan;
import java.time.Instant;
import io.corebilling.platform.api.error.NotFoundException;

/** Which version of a plan applies to a request. */
final class PlanSelection {

    private PlanSelection() {}

    /** The named version, or the one in force at {@code at}. */
    static PlanVersion choose(PricingPlan plan, Integer number, Instant at) {
        if (number != null) {
            return plan.version(number).orElseThrow(() -> new io.corebilling.platform.api.error.NotFoundException("plan version not found"));
        }
        return plan.effectiveAt(at).orElseThrow(() -> new ConflictException("no version of plan '" + plan.code() + "' is in force at " + at));
    }
}
