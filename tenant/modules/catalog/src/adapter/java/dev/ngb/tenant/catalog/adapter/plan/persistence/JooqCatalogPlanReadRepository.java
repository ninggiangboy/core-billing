package dev.ngb.tenant.catalog.adapter.plan.persistence;

import static dev.ngb.tenant_jooq.Tables.CATALOG_PLANS;

import dev.ngb.tenant.catalog.application.plan.query.CatalogPlanReadRepository;
import dev.ngb.tenant.catalog.application.plan.query.PlanView;
import dev.ngb.tenant.catalog.domain.plan.ResourceType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

/** The read side: selects straight into the view the controller renders. */
public class JooqCatalogPlanReadRepository implements CatalogPlanReadRepository {

    private final DSLContext dsl;

    public JooqCatalogPlanReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<PlanView> listActive() {
        return select(CATALOG_PLANS.ACTIVE.isTrue());
    }

    @Override
    public List<PlanView> all() {
        return select(null);
    }

    @Override
    public Optional<PlanView> byId(UUID id) {
        return select(CATALOG_PLANS.ID.eq(id)).stream().findFirst();
    }

    @Override
    public Optional<PlanView> byCode(String code) {
        return select(CATALOG_PLANS.CODE.eq(code)).stream().findFirst();
    }

    private List<PlanView> select(org.jooq.Condition condition) {
        return dsl.select(
                        CATALOG_PLANS.ID,
                        CATALOG_PLANS.CODE,
                        CATALOG_PLANS.NAME,
                        CATALOG_PLANS.RESOURCE_TYPE,
                        CATALOG_PLANS.DESCRIPTION,
                        CATALOG_PLANS.BILLING_PLAN_ID,
                        CATALOG_PLANS.ACTIVE,
                        CATALOG_PLANS.CREATED_AT)
                .from(CATALOG_PLANS)
                .where(condition == null ? DSL.noCondition() : condition)
                .orderBy(CATALOG_PLANS.RESOURCE_TYPE.asc())
                .fetch(row -> new PlanView(
                        row.get(CATALOG_PLANS.ID),
                        row.get(CATALOG_PLANS.CODE),
                        row.get(CATALOG_PLANS.NAME),
                        ResourceType.valueOf(row.get(CATALOG_PLANS.RESOURCE_TYPE)),
                        row.get(CATALOG_PLANS.DESCRIPTION),
                        row.get(CATALOG_PLANS.BILLING_PLAN_ID),
                        row.get(CATALOG_PLANS.ACTIVE),
                        row.get(CATALOG_PLANS.CREATED_AT)));
    }
}
