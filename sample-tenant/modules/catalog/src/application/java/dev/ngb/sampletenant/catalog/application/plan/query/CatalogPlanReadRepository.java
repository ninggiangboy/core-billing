package dev.ngb.sampletenant.catalog.application.plan.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read port for the offer catalog; it selects straight into views. */
public interface CatalogPlanReadRepository {

    List<PlanView> listActive();

    List<PlanView> all();

    Optional<PlanView> byId(UUID id);

    Optional<PlanView> byCode(String code);
}
