package dev.ngb.core_billing.pricing.adapter.plan.persistence;

import static dev.ngb.core_billing_jooq.Tables.PRICING_PLANS;
import static dev.ngb.core_billing_jooq.Tables.PRICING_PLAN_VERSIONS;
import static org.jooq.impl.DSL.noCondition;

import dev.ngb.core_billing.pricing.api.plan.BillingCycle;
import dev.ngb.core_billing.pricing.application.plan.query.PlanReadRepository;
import dev.ngb.core_billing.pricing.application.plan.query.PlanVersionView;
import dev.ngb.core_billing.pricing.application.plan.query.PricingPlanView;
import dev.ngb.core_billing.pricing.domain.rule.PricingModel;
import dev.ngb.core_billing.pricing.domain.plan.PlanStatus;
import dev.ngb.core_billing.pricing.domain.plan.RoundingPolicy;
import dev.ngb.core_billing_jooq.tables.records.PricingPlanVersionsRecord;
import dev.ngb.core_billing_jooq.tables.records.PricingPlansRecord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;

/** Selects plans and their versions straight into views; no domain plan is built. */
public class JooqPlanReadRepository implements PlanReadRepository {

    private final DSLContext dsl;

    public JooqPlanReadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<PricingPlanView> list(PlanStatus status) {
        List<PricingPlansRecord> rows = dsl.selectFrom(PRICING_PLANS)
                .where(status == null ? noCondition() : PRICING_PLANS.STATUS.eq(status.name()))
                .orderBy(PRICING_PLANS.ID.desc())
                .fetch();
        return withVersions(rows);
    }

    @Override
    public Optional<PricingPlanView> findById(UUID id) {
        return dsl.selectFrom(PRICING_PLANS)
                .where(PRICING_PLANS.ID.eq(id))
                .fetchOptional()
                .map(row -> withVersions(List.of(row)).getFirst());
    }

    private List<PricingPlanView> withVersions(List<PricingPlansRecord> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<PlanVersionView>> versions = new HashMap<>();
        dsl.selectFrom(PRICING_PLAN_VERSIONS)
                .where(PRICING_PLAN_VERSIONS.PLAN_ID.in(rows.stream().map(PricingPlansRecord::getId).toList()))
                .orderBy(PRICING_PLAN_VERSIONS.PLAN_ID, PRICING_PLAN_VERSIONS.VERSION)
                .forEach(row -> versions.computeIfAbsent(row.getPlanId(), key -> new ArrayList<>()).add(toView(row)));
        return rows.stream()
                .map(row -> new PricingPlanView(
                        row.getId(),
                        row.getCode(),
                        row.getName(),
                        PricingModel.valueOf(row.getModel()),
                        row.getCurrency().strip(),
                        PlanStatus.valueOf(row.getStatus()),
                        row.getCreatedAt(),
                        row.getRetiredAt(),
                        versions.getOrDefault(row.getId(), List.of())))
                .toList();
    }

    private static PlanVersionView toView(PricingPlanVersionsRecord row) {
        return new PlanVersionView(
                row.getPlanId(),
                row.getVersion(),
                row.getRule().data(),
                row.getBillingCycle() == null ? null : BillingCycle.valueOf(row.getBillingCycle()),
                row.getMinCharge(),
                row.getMaxCharge(),
                RoundingPolicy.valueOf(row.getRounding()),
                row.getValidFrom(),
                row.getValidUntil(),
                row.getCreatedAt(),
                row.getCreatedBy());
    }
}
