package dev.ngb.core_billing.pricing.domain.rating;

import dev.ngb.core_billing.pricing.domain.rating.UsageRating;
import dev.ngb.core_billing.sharedkernel.DomainEvent;
import dev.ngb.core_billing.sharedkernel.TenantId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** What rating announces. Payloads are the integration contract: ids, amounts in minor units and codes, no personal data. */
public final class RatingEvents {

    private RatingEvents() {}

    private static Map<String, Object> payload(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
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
            return RatingEvents.payload("ratingId", rating.id().toString(), "customerId", rating.customerId().toString(), "walletId", rating.walletId().toString(),
                    "planId", rating.planId().toString(), "planVersion", rating.planVersion(), "status", rating.status().name(),
                    "amount", Map.of("minorUnits", rating.amount().minorUnits(), "currency", rating.amount().currency().code()),
                    "chargeId", rating.chargeId() == null ? null : rating.chargeId().toString(), "usageCount", rating.usageCount(),
                    "periodStart", rating.periodStart().toString(), "periodEnd", rating.periodEnd().toString());
        }
    }
}
