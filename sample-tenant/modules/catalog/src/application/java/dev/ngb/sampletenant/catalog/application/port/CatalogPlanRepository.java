package dev.ngb.sampletenant.catalog.application.port;

import dev.ngb.sampletenant.catalog.domain.CatalogPlan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for the local offer catalog. */
public interface CatalogPlanRepository {

    void save(CatalogPlan plan);

    Optional<CatalogPlan> findById(UUID id);

    Optional<CatalogPlan> findByCode(String code);

    List<CatalogPlan> findAll();
}
