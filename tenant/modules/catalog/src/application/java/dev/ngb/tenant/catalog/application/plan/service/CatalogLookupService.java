package dev.ngb.tenant.catalog.application.plan.service;

import dev.ngb.tenant.catalog.api.lookup.CatalogLookup;
import dev.ngb.tenant.catalog.application.plan.query.CatalogPlanReadRepository;
import dev.ngb.tenant.catalog.application.plan.query.PlanView;
import dev.ngb.tenant.shared.error.NotFoundException;
import java.util.UUID;

/** Serves the catalog facts other modules need, through the query side. */
public class CatalogLookupService implements CatalogLookup {

    private final CatalogPlanReadRepository plans;

    public CatalogLookupService(CatalogPlanReadRepository plans) {
        this.plans = plans;
    }

    @Override
    public PlanSnapshot get(UUID id) {
        PlanView plan = plans.byId(id).orElseThrow(() -> new NotFoundException("catalog plan not found"));
        return new PlanSnapshot(
                plan.id(),
                plan.code(),
                plan.name(),
                plan.billingPlanId(),
                plan.resourceType().name(),
                plan.resourceType().pricingModel().name(),
                plan.resourceType().metric(),
                plan.resourceType().unit(),
                plan.billable());
    }
}
