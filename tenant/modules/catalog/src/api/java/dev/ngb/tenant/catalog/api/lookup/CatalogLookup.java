package dev.ngb.tenant.catalog.api.lookup;

import java.util.UUID;

/** The catalog facts the ordering flow needs, without exposing the catalog module. */
public interface CatalogLookup {

    record PlanSnapshot(
            UUID id,
            String code,
            String name,
            String billingPlanId,
            String resourceType,
            String pricingModel,
            String metric,
            String unit,
            boolean billable) {}

    PlanSnapshot get(UUID id);
}
