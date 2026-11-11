package dev.ngb.sampletenant.catalog.adapter.persistence;

import dev.ngb.sampletenant.catalog.application.port.CatalogPlanRepository;
import dev.ngb.sampletenant.catalog.domain.CatalogPlan;
import dev.ngb.sampletenant.catalog.domain.ResourceType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class JdbcCatalogPlanRepository implements CatalogPlanRepository {

    private final CatalogPlanJdbcRepository rows;

    public JdbcCatalogPlanRepository(CatalogPlanJdbcRepository rows) {
        this.rows = rows;
    }

    @Override
    public void save(CatalogPlan plan) {
        rows.save(new CatalogPlanRow(
                plan.id(),
                plan.code(),
                plan.name(),
                plan.resourceType().name(),
                plan.resourceType().pricingModel().name(),
                plan.description(),
                plan.billingPlanId(),
                plan.active(),
                plan.createdAt()));
    }

    @Override
    public Optional<CatalogPlan> findById(UUID id) {
        return rows.findById(id).map(JdbcCatalogPlanRepository::toDomain);
    }

    @Override
    public Optional<CatalogPlan> findByCode(String code) {
        return rows.findByCode(code).map(JdbcCatalogPlanRepository::toDomain);
    }

    @Override
    public List<CatalogPlan> findAll() {
        return rows.findAllByOrderByResourceTypeAsc().stream().map(JdbcCatalogPlanRepository::toDomain).toList();
    }

    private static CatalogPlan toDomain(CatalogPlanRow row) {
        return new CatalogPlan(
                row.id(),
                row.code(),
                row.name(),
                ResourceType.valueOf(row.resourceType()),
                row.description(),
                row.billingPlanId(),
                row.active(),
                row.createdAt());
    }
}
