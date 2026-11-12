package dev.ngb.tenant.catalog.application.plan.port;

import dev.ngb.tenant.catalog.domain.plan.CatalogPlan;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for the local offer catalog. */
public interface CatalogPlanRepository {

    void save(CatalogPlan plan);

    Optional<CatalogPlan> findById(UUID id);

    Optional<CatalogPlan> findByCode(String code);
}
