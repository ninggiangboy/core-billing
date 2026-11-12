package dev.ngb.billing.invoicing.application.cycle.service;

import dev.ngb.billing.pricing.api.plan.BillingCycle;

/** How Postpaid charges are grouped, and how many periods one job run closes. */
public record CycleSettings(BillingCycle cycle, int batchSize) {

    public CycleSettings {
        if (cycle == null || batchSize < 1) {
            throw new IllegalArgumentException("a cycle and a positive batch size are required");
        }
    }
}
