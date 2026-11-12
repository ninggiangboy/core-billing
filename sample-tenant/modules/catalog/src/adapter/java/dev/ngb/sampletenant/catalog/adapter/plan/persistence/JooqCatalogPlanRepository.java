package dev.ngb.sampletenant.catalog.adapter.plan.persistence;

import static dev.ngb.sampletenant_jooq.Tables.CATALOG_PLANS;

import dev.ngb.sampletenant.catalog.application.plan.port.CatalogPlanRepository;
import dev.ngb.sampletenant.catalog.domain.plan.CatalogPlan;
import dev.ngb.sampletenant.catalog.domain.plan.ResourceType;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

/** The write side: an upsert and the lookups a use case decides with. */
public class JooqCatalogPlanRepository implements CatalogPlanRepository {

    private final DSLContext dsl;

    public JooqCatalogPlanRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void save(CatalogPlan plan) {
        dsl.insertInto(CATALOG_PLANS)
                .columns(
                        CATALOG_PLANS.ID,
                        CATALOG_PLANS.CODE,
                        CATALOG_PLANS.NAME,
                        CATALOG_PLANS.RESOURCE_TYPE,
                        CATALOG_PLANS.PRICING_MODEL,
                        CATALOG_PLANS.DESCRIPTION,
                        CATALOG_PLANS.BILLING_PLAN_ID,
                        CATALOG_PLANS.ACTIVE,
                        CATALOG_PLANS.CREATED_AT)
                .values(
                        plan.id(),
                        plan.code(),
                        plan.name(),
                        plan.resourceType().name(),
                        plan.resourceType().pricingModel().name(),
                        plan.description(),
                        plan.billingPlanId(),
                        plan.active(),
                        plan.createdAt())
                .onConflict(CATALOG_PLANS.ID)
                .doUpdate()
                .set(CATALOG_PLANS.CODE, DSL.excluded(CATALOG_PLANS.CODE))
                .set(CATALOG_PLANS.NAME, DSL.excluded(CATALOG_PLANS.NAME))
                .set(CATALOG_PLANS.RESOURCE_TYPE, DSL.excluded(CATALOG_PLANS.RESOURCE_TYPE))
                .set(CATALOG_PLANS.PRICING_MODEL, DSL.excluded(CATALOG_PLANS.PRICING_MODEL))
                .set(CATALOG_PLANS.DESCRIPTION, DSL.excluded(CATALOG_PLANS.DESCRIPTION))
                .set(CATALOG_PLANS.BILLING_PLAN_ID, DSL.excluded(CATALOG_PLANS.BILLING_PLAN_ID))
                .set(CATALOG_PLANS.ACTIVE, DSL.excluded(CATALOG_PLANS.ACTIVE))
                .execute();
    }

    @Override
    public Optional<CatalogPlan> findById(UUID id) {
        return dsl.selectFrom(CATALOG_PLANS)
                .where(CATALOG_PLANS.ID.eq(id))
                .fetchOptional()
                .map(JooqCatalogPlanRepository::toDomain);
    }

    @Override
    public Optional<CatalogPlan> findByCode(String code) {
        return dsl.selectFrom(CATALOG_PLANS)
                .where(CATALOG_PLANS.CODE.eq(code))
                .fetchOptional()
                .map(JooqCatalogPlanRepository::toDomain);
    }

    private static CatalogPlan toDomain(dev.ngb.sampletenant_jooq.tables.records.CatalogPlansRecord row) {
        return new CatalogPlan(
                row.getId(),
                row.getCode(),
                row.getName(),
                ResourceType.valueOf(row.getResourceType()),
                row.getDescription(),
                row.getBillingPlanId(),
                row.getActive(),
                row.getCreatedAt());
    }
}
