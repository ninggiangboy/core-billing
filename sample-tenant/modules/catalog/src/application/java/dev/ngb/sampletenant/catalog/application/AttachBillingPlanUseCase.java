package dev.ngb.sampletenant.catalog.application;

import dev.ngb.sampletenant.catalog.application.port.CatalogPlanRepository;
import dev.ngb.sampletenant.catalog.domain.CatalogPlan;
import dev.ngb.sampletenant.shared.error.BadRequestException;
import dev.ngb.sampletenant.shared.error.NotFoundException;

/** Binds a local catalog plan to the Billing plan the bootstrap created for it. */
public class AttachBillingPlanUseCase {

    private final CatalogPlanRepository plans;

    public AttachBillingPlanUseCase(CatalogPlanRepository plans) {
        this.plans = plans;
    }

    public CatalogPlan attachByCode(String code, String billingPlanId) {
        if (billingPlanId == null || billingPlanId.isBlank()) {
            throw new BadRequestException("a Billing plan id is required");
        }
        CatalogPlan plan = plans.findByCode(code).orElseThrow(() -> new NotFoundException("catalog plan not found: " + code));
        CatalogPlan updated = plan.withBillingPlan(billingPlanId);
        plans.save(updated);
        return updated;
    }
}
