package dev.ngb.sampletenant.catalog.application;

import dev.ngb.sampletenant.catalog.application.port.CatalogPlanRepository;
import dev.ngb.sampletenant.catalog.domain.CatalogPlan;
import dev.ngb.sampletenant.shared.error.NotFoundException;
import java.util.List;
import java.util.UUID;

/** Read side of the offer catalog. */
public class CatalogQueries {

    private final CatalogPlanRepository plans;

    public CatalogQueries(CatalogPlanRepository plans) {
        this.plans = plans;
    }

    public List<CatalogPlan> listActive() {
        return plans.findAll().stream().filter(CatalogPlan::active).toList();
    }

    public CatalogPlan get(UUID id) {
        return plans.findById(id).orElseThrow(() -> new NotFoundException("catalog plan not found"));
    }

    public CatalogPlan getByCode(String code) {
        return plans.findByCode(code).orElseThrow(() -> new NotFoundException("catalog plan not found"));
    }
}
