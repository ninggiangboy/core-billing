package dev.ngb.sampletenant.catalog.application.plan.service;

import dev.ngb.sampletenant.catalog.api.lookup.CatalogLookup;
import dev.ngb.sampletenant.catalog.application.plan.query.CatalogQueries;
import java.util.UUID;

/** Serves the catalog facts other modules need, through the query side. */
public class CatalogLookupService implements CatalogLookup {

    private final CatalogQueries plans;

    public CatalogLookupService(CatalogQueries plans) {
        this.plans = plans;
    }

    @Override
    public PlanSnapshot get(UUID id) {
        return plans.get(id);
    }
}
