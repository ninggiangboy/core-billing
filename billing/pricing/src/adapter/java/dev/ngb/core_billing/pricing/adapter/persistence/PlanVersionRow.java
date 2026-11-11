package dev.ngb.core_billing.pricing.adapter.persistence;

import dev.ngb.core_billing.pricing.api.plan.BillingCycle;
import dev.ngb.core_billing.pricing.domain.plan.PlanId;
import dev.ngb.core_billing.pricing.domain.plan.PlanVersion;
import dev.ngb.core_billing.pricing.domain.model.PricingModel;
import dev.ngb.core_billing.pricing.domain.plan.RoundingPolicy;
import dev.ngb.core_billing.sharedkernel.TenantId;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.relational.core.mapping.Table;
import tools.jackson.databind.json.JsonMapper;

@Table("pricing_plan_versions")
record PlanVersionRow(
        UUID planId,
        int version,
        UUID tenantId,
        String rule,
        String billingCycle,
        Long minCharge,
        Long maxCharge,
        String rounding,
        Instant validFrom,
        Instant validUntil,
        Instant createdAt,
        String createdBy) {

    static PlanVersionRow from(PlanVersion version, JsonMapper json) {
        return new PlanVersionRow(
                version.planId().value(),
                version.number(),
                version.tenantId().value(),
                json.writeValueAsString(RuleDto.of(version.rule())),
                version.billingCycle() == null ? null : version.billingCycle().name(),
                version.minCharge(),
                version.maxCharge(),
                version.rounding().name(),
                version.validFrom(),
                version.validUntil(),
                version.createdAt(),
                version.createdBy());
    }

    PlanVersion toDomain(PricingModel model, JsonMapper json) {
        return new PlanVersion(
                new PlanId(planId),
                new TenantId(tenantId),
                version,
                json.readValue(rule, RuleDto.class).toRule(model),
                billingCycle == null ? null : BillingCycle.valueOf(billingCycle),
                minCharge,
                maxCharge,
                RoundingPolicy.valueOf(rounding),
                validFrom,
                validUntil,
                createdAt,
                createdBy);
    }
}
