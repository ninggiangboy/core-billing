package dev.ngb.sampletenant.catalog.adapter.plan.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

public interface CatalogPlanJdbcRepository extends CrudRepository<CatalogPlanRow, UUID> {

    Optional<CatalogPlanRow> findByCode(String code);

    List<CatalogPlanRow> findAllByOrderByResourceTypeAsc();
}
