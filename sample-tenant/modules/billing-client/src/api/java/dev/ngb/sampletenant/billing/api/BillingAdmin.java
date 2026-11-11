package dev.ngb.sampletenant.billing.api;

import dev.ngb.sampletenant.billing.api.model.PricingPlan;
import dev.ngb.sampletenant.billing.api.model.Rule;
import java.time.Instant;
import java.util.List;

/** The administrator calls the bootstrap uses to publish the reference pricing plans. */
public interface BillingAdmin {

    record VersionRequest(
            Rule rule,
            String billingCycle,
            Long minCharge,
            Long maxCharge,
            String rounding,
            Instant validFrom,
            Instant validUntil) {}

    record CreatePlanRequest(String code, String name, String currency, String model, VersionRequest version) {}

    List<PricingPlan> plans(String status);

    PricingPlan createPlan(CreatePlanRequest request);
}
