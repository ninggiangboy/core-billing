package dev.ngb.sampletenant.catalog.application.plan.query;

import dev.ngb.sampletenant.catalog.api.lookup.CatalogLookup;
import dev.ngb.sampletenant.catalog.application.plan.port.CatalogPlanRepository;
import dev.ngb.sampletenant.catalog.domain.plan.CatalogPlan;
import dev.ngb.sampletenant.shared.error.NotFoundException;
import java.util.List;
import java.util.UUID;

/** Read side of the offer catalog. */
public class CatalogQueries {

    private final CatalogPlanRepository plans;

    public CatalogQueries(CatalogPlanRepository plans) {
        this.plans = plans;
    }

    public CatalogLookup.PlanSnapshot get(UUID id) {
        return snapshot(plan(id));
    }

    public List<CatalogPlan> listActive() {
        return plans.findAll().stream().filter(CatalogPlan::active).toList();
    }

    public List<CatalogPlan> all() {
        return plans.findAll();
    }

    public CatalogPlan plan(UUID id) {
        return plans.findById(id).orElseThrow(() -> new NotFoundException("catalog plan not found"));
    }

    public CatalogPlan getByCode(String code) {
        return plans.findByCode(code).orElseThrow(() -> new NotFoundException("catalog plan not found"));
    }

    private static CatalogLookup.PlanSnapshot snapshot(CatalogPlan plan) {
        return new CatalogLookup.PlanSnapshot(
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
