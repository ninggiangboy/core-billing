package dev.ngb.core_billing.invoicing.application.cycle.command;

import dev.ngb.core_billing.pricing.api.plan.BillingCycle;

/** How Postpaid charges are grouped, and how many periods one job run closes. */
public record CycleSettings(BillingCycle cycle, int batchSize) {

    public CycleSettings {
        if (cycle == null || batchSize < 1) {
            throw new IllegalArgumentException("a cycle and a positive batch size are required");
        }
    }
}
