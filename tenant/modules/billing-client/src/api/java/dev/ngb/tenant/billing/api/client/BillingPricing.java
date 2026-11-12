package dev.ngb.tenant.billing.api.client;

import dev.ngb.tenant.billing.api.model.Estimate;
import dev.ngb.tenant.billing.api.model.PricingPlan;
import dev.ngb.tenant.billing.api.model.ReserveResult;
import dev.ngb.tenant.billing.api.model.UsageInput;
import java.time.Instant;
import java.util.List;

/** Plans, pure estimates and reserve-from-estimate. */
public interface BillingPricing {

    record EstimateRequest(String planId, Integer version, Instant at, List<UsageInput> usage) {}

    record ReserveRequest(String walletId, long amount, String currency, Long ttlSeconds, String reference) {}

    List<PricingPlan> plans(String status);

    PricingPlan plan(String id);

    Estimate estimate(EstimateRequest request);

    ReserveResult reserve(String idempotencyKey, ReserveRequest request);
}
