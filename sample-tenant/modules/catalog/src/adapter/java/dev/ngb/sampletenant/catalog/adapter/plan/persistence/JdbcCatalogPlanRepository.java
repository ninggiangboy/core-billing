package dev.ngb.sampletenant.catalog.adapter.plan.persistence;

import dev.ngb.sampletenant.catalog.application.plan.port.CatalogPlanRepository;
import dev.ngb.sampletenant.catalog.domain.plan.CatalogPlan;
import dev.ngb.sampletenant.catalog.domain.plan.ResourceType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcCatalogPlanRepository implements CatalogPlanRepository {

    private final CatalogPlanJdbcRepository rows;
    private final JdbcTemplate jdbc;

    public JdbcCatalogPlanRepository(CatalogPlanJdbcRepository rows, JdbcTemplate jdbc) {
        this.rows = rows;
        this.jdbc = jdbc;
    }

    @Override
    public void save(CatalogPlan plan) {
        jdbc.update(
                """
                insert into catalog_plans
                    (id, code, name, resource_type, pricing_model, description, billing_plan_id, active, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (id) do update
                    set billing_plan_id = excluded.billing_plan_id,
                        active = excluded.active
                """,
                plan.id(),
                plan.code(),
                plan.name(),
                plan.resourceType().name(),
                plan.resourceType().pricingModel().name(),
                plan.description(),
                plan.billingPlanId(),
                plan.active(),
                java.sql.Timestamp.from(plan.createdAt()));
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
