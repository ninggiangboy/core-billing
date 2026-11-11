package dev.ngb.core_billing.pricing.adapter.persistence.plan;

import static dev.ngb.core_billing_jooq.Tables.PRICING_PLANS;
import static dev.ngb.core_billing_jooq.Tables.PRICING_PLAN_VERSIONS;

import dev.ngb.core_billing.pricing.application.plan.port.PlanRepository;
import dev.ngb.core_billing.pricing.api.plan.BillingCycle;
import dev.ngb.core_billing.pricing.domain.rule.PricingModel;
import dev.ngb.core_billing.pricing.domain.plan.PlanId;
import dev.ngb.core_billing.pricing.domain.plan.PlanStatus;
import dev.ngb.core_billing.pricing.domain.plan.PlanVersion;
import dev.ngb.core_billing.pricing.domain.plan.PricingPlan;
import dev.ngb.core_billing.pricing.domain.plan.RoundingPolicy;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.TenantId;
import dev.ngb.core_billing_jooq.tables.records.PricingPlanVersionsRecord;
import dev.ngb.core_billing_jooq.tables.records.PricingPlansRecord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import tools.jackson.databind.json.JsonMapper;

public class JooqPlanRepository implements PlanRepository {

    private final DSLContext dsl;
    private final JsonMapper json;

    public JooqPlanRepository(DSLContext dsl, JsonMapper json) {
        this.dsl = dsl;
        this.json = json;
    }

    @Override
    public void add(PricingPlan plan) {
        dsl.insertInto(PRICING_PLANS)
                .set(PRICING_PLANS.ID, plan.id().value())
                .set(PRICING_PLANS.TENANT_ID, plan.tenantId().value())
                .set(PRICING_PLANS.CODE, plan.code())
                .set(PRICING_PLANS.NAME, plan.name())
                .set(PRICING_PLANS.MODEL, plan.model().name())
                .set(PRICING_PLANS.CURRENCY, plan.currency().code())
                .set(PRICING_PLANS.STATUS, plan.status().name())
                .set(PRICING_PLANS.CREATED_AT, plan.createdAt())
                .set(PRICING_PLANS.RETIRED_AT, plan.retiredAt())
                .execute();
        plan.versions().forEach(this::addVersion);
    }

    @Override
    public void addVersion(PlanVersion version) {
        dsl.insertInto(PRICING_PLAN_VERSIONS)
                .set(PRICING_PLAN_VERSIONS.PLAN_ID, version.planId().value())
                .set(PRICING_PLAN_VERSIONS.VERSION, version.number())
                .set(PRICING_PLAN_VERSIONS.TENANT_ID, version.tenantId().value())
                .set(PRICING_PLAN_VERSIONS.RULE, JSONB.valueOf(json.writeValueAsString(RuleDto.of(version.rule()))))
                .set(PRICING_PLAN_VERSIONS.BILLING_CYCLE, version.billingCycle() == null ? null : version.billingCycle().name())
                .set(PRICING_PLAN_VERSIONS.MIN_CHARGE, version.minCharge())
                .set(PRICING_PLAN_VERSIONS.MAX_CHARGE, version.maxCharge())
                .set(PRICING_PLAN_VERSIONS.ROUNDING, version.rounding().name())
                .set(PRICING_PLAN_VERSIONS.VALID_FROM, version.validFrom())
                .set(PRICING_PLAN_VERSIONS.VALID_UNTIL, version.validUntil())
                .set(PRICING_PLAN_VERSIONS.CREATED_AT, version.createdAt())
                .set(PRICING_PLAN_VERSIONS.CREATED_BY, version.createdBy())
                .execute();
    }

    @Override
    public void update(PricingPlan plan) {
        dsl.update(PRICING_PLANS)
                .set(PRICING_PLANS.NAME, plan.name())
                .set(PRICING_PLANS.MODEL, plan.model().name())
                .set(PRICING_PLANS.CURRENCY, plan.currency().code())
                .set(PRICING_PLANS.STATUS, plan.status().name())
                .set(PRICING_PLANS.RETIRED_AT, plan.retiredAt())
                .where(PRICING_PLANS.ID.eq(plan.id().value()))
                .execute();
    }

    @Override
    public Optional<PricingPlan> findById(PlanId id) {
        return dsl.selectFrom(PRICING_PLANS)
                .where(PRICING_PLANS.ID.eq(id.value()))
                .fetchOptional()
                .map(row -> withVersions(List.of(row)).getFirst());
    }

    @Override
    public Optional<PricingPlan> lockById(PlanId id) {
        return dsl.selectFrom(PRICING_PLANS)
                .where(PRICING_PLANS.ID.eq(id.value()))
                .forUpdate()
                .fetchOptional()
                .map(row -> withVersions(List.of(row)).getFirst());
    }

    @Override
    public Optional<PricingPlan> findByCode(String code) {
        return dsl.selectFrom(PRICING_PLANS)
                .where(PRICING_PLANS.CODE.eq(code))
                .fetchOptional()
                .map(row -> withVersions(List.of(row)).getFirst());
    }

    private List<PricingPlan> withVersions(List<PricingPlansRecord> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, PricingModel> models = new HashMap<>();
        rows.forEach(row -> models.put(row.getId(), PricingModel.valueOf(row.getModel())));
        Map<UUID, List<PlanVersion>> versions = new HashMap<>();
        dsl.selectFrom(PRICING_PLAN_VERSIONS)
                .where(PRICING_PLAN_VERSIONS.PLAN_ID.in(models.keySet()))
                .orderBy(PRICING_PLAN_VERSIONS.PLAN_ID, PRICING_PLAN_VERSIONS.VERSION)
                .forEach(row -> versions.computeIfAbsent(row.getPlanId(), key -> new ArrayList<>())
                        .add(toDomain(row, models.get(row.getPlanId()))));
        return rows.stream().map(row -> toDomain(row, versions.getOrDefault(row.getId(), List.of()))).toList();
    }

    private static PricingPlan toDomain(PricingPlansRecord row, List<PlanVersion> versions) {
        return PricingPlan.restore(
                new PlanId(row.getId()),
                new TenantId(row.getTenantId()),
                row.getCode(),
                row.getName(),
                PricingModel.valueOf(row.getModel()),
                Currency.of(row.getCurrency().strip()),
                PlanStatus.valueOf(row.getStatus()),
                row.getCreatedAt(),
                row.getRetiredAt(),
                versions);
    }

    private PlanVersion toDomain(PricingPlanVersionsRecord row, PricingModel model) {
        return new PlanVersion(
                new PlanId(row.getPlanId()),
                new TenantId(row.getTenantId()),
                row.getVersion(),
                json.readValue(row.getRule().data(), RuleDto.class).toRule(model),
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
