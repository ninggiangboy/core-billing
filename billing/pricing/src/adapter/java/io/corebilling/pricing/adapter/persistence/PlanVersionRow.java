package io.corebilling.pricing.adapter.persistence;

import io.corebilling.pricing.domain.plan.BillingCycle;
import io.corebilling.pricing.domain.plan.PlanId;
import io.corebilling.pricing.domain.plan.PlanVersion;
import io.corebilling.pricing.domain.model.PricingModel;
import io.corebilling.pricing.domain.plan.RoundingPolicy;
import io.corebilling.sharedkernel.TenantId;
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
