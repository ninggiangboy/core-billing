package io.corebilling.pricing.domain.plan;

import io.corebilling.sharedkernel.DomainEvent;
import io.corebilling.sharedkernel.TenantId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import io.corebilling.pricing.domain.rating.UsageRating;

/** What pricing announces. Payloads are the integration contract: ids, amounts in minor units and codes, no personal data. */
public final class PricingEvents {

    private PricingEvents() {}

    private static Map<String, Object> payload(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    /** A new plan version exists (the first version of a new plan, or a later one). */
    public record PlanVersionPublished(UUID eventId, PricingPlan plan, PlanVersion version, Instant occurredAt) implements DomainEvent {
        public static final String TYPE = "pricing-plan.version-published";

        @Override
        public TenantId tenantId() {
            return plan.tenantId();
        }

        @Override
        public String type() {
            return TYPE;
        }

        @Override
        public String aggregate() {
            return "pricing-plan/" + plan.id();
        }

        @Override
        public Map<String, Object> payload() {
            return PricingEvents.payload("planId", plan.id().toString(), "code", plan.code(), "model", plan.model().name(), "currency", plan.currency().code(),
                    "version", version.number(), "billingCycle", version.billingCycle() == null ? null : version.billingCycle().name(),
                    "validFrom", version.validFrom().toString(), "validUntil", version.validUntil() == null ? null : version.validUntil().toString());
        }
    }

    public record PlanRetired(UUID eventId, PricingPlan plan, Instant occurredAt) implements DomainEvent {
        public static final String TYPE = "pricing-plan.retired";

        @Override
        public TenantId tenantId() {
            return plan.tenantId();
        }

        @Override
        public String type() {
            return TYPE;
        }

        @Override
        public String aggregate() {
            return "pricing-plan/" + plan.id();
        }

        @Override
        public Map<String, Object> payload() {
            return PricingEvents.payload("planId", plan.id().toString(), "code", plan.code());
        }
    }

    /** Usage was priced and accounted for: charged to the wallet, or rated to nothing. A rating whose charge failed announces {@code charge.failed} instead. */
    public record UsageRated(UUID eventId, UsageRating rating, Instant occurredAt) implements DomainEvent {
        public static final String TYPE = "usage.rated";

        @Override
        public TenantId tenantId() {
            return rating.tenantId();
        }

        @Override
        public String type() {
            return TYPE;
        }

        @Override
        public String aggregate() {
            return "usage-rating/" + rating.id();
        }

        @Override
        public Map<String, Object> payload() {
            return PricingEvents.payload("ratingId", rating.id().toString(), "customerId", rating.customerId().toString(), "walletId", rating.walletId().toString(),
                    "planId", rating.planId().toString(), "planVersion", rating.planVersion(), "status", rating.status().name(),
                    "amount", Map.of("minorUnits", rating.amount().minorUnits(), "currency", rating.amount().currency().code()),
                    "chargeId", rating.chargeId() == null ? null : rating.chargeId().toString(), "usageCount", rating.usageCount(),
                    "periodStart", rating.periodStart().toString(), "periodEnd", rating.periodEnd().toString());
        }
    }
}
