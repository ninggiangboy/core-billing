package io.corebilling.pricing.adapter.persistence;

import io.corebilling.pricing.domain.plan.PlanId;
import io.corebilling.pricing.domain.plan.PlanStatus;
import io.corebilling.pricing.domain.plan.PlanVersion;
import io.corebilling.pricing.domain.plan.PricingModel;
import io.corebilling.pricing.domain.plan.PricingPlan;
import io.corebilling.sharedkernel.Currency;
import io.corebilling.sharedkernel.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("pricing_plans")
record PricingPlanRow(
        @Id UUID id,
        UUID tenantId,
        String code,
        String name,
        String model,
        String currency,
        String status,
        Instant createdAt,
        Instant retiredAt) {

    static PricingPlanRow from(PricingPlan plan) {
        return new PricingPlanRow(
                plan.id().value(),
                plan.tenantId().value(),
                plan.code(),
                plan.name(),
                plan.model().name(),
                plan.currency().code(),
                plan.status().name(),
                plan.createdAt(),
                plan.retiredAt());
    }

    PricingPlan toDomain(List<PlanVersion> versions) {
        return PricingPlan.restore(
                new PlanId(id),
                new TenantId(tenantId),
                code,
                name,
                PricingModel.valueOf(model),
                Currency.of(currency.strip()),
                PlanStatus.valueOf(status),
                createdAt,
                retiredAt,
                versions);
    }

    PricingModel pricingModel() {
        return PricingModel.valueOf(model);
    }
}
