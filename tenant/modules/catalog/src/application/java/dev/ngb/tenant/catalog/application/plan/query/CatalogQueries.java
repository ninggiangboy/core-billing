package dev.ngb.tenant.catalog.application.plan.query;

import dev.ngb.tenant.shared.error.NotFoundException;
import java.util.List;
import java.util.UUID;

/** Read side of the offer catalog. */
public class CatalogQueries {

    private final CatalogPlanReadRepository plans;

    public CatalogQueries(CatalogPlanReadRepository plans) {
        this.plans = plans;
    }

    public List<PlanView> listActive() {
        return plans.listActive();
    }

    public List<PlanView> all() {
        return plans.all();
    }

    public PlanView plan(UUID id) {
        return plans.byId(id).orElseThrow(() -> new NotFoundException("catalog plan not found"));
    }

    public PlanView getByCode(String code) {
        return plans.byCode(code).orElseThrow(() -> new NotFoundException("catalog plan not found"));
    }
}
