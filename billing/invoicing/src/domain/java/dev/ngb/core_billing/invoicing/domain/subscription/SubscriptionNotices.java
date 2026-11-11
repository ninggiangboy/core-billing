package dev.ngb.core_billing.invoicing.domain.subscription;

import dev.ngb.core_billing.sharedkernel.DomainEvent;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.TenantId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What the invoicing context tells the Tenant System about a subscription, written to the outbox in the transaction
 * of the change it reports. A notice only reports a fact; what to provision or stop is the Tenant System's decision.
 */
public final class SubscriptionNotices {

    private SubscriptionNotices() {}

    private static Map<String, Object> money(Money money) {
        return Map.of("minorUnits", money.minorUnits(), "currency", money.currency().code());
    }

    private static Map<String, Object> common(Subscription subscription, int periodNumber, Money amount) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("subscriptionId", subscription.id().toString());
        map.put("customerId", subscription.customerId().toString());
        map.put("walletId", subscription.walletId().toString());
        map.put("planId", subscription.planId().toString());
        map.put("planCode", subscription.planCode());
        map.put("periodNumber", periodNumber);
        map.put("periodStart", subscription.periodStart(periodNumber).toString());
        map.put("periodEnd", subscription.periodEnd(periodNumber).toString());
        map.put("amount", money(amount));
        return map;
    }

    /**
     * A period was paid. Period 0 is the first one and is reported when the subscription starts.
     * {@code chargeId} is null only for a plan whose recurring price is zero.
     */
    public record Renewed(UUID eventId, Subscription subscription, int periodNumber, Money amount, UUID chargeId, Instant occurredAt) implements DomainEvent {
        public static final String TYPE = "subscription.renewed";

        @Override
        public TenantId tenantId() {
            return subscription.tenantId();
        }

        @Override
        public String type() {
            return TYPE;
        }

        @Override
        public String aggregate() {
            return "subscription/" + subscription.id();
        }

        @Override
        public Map<String, Object> payload() {
            Map<String, Object> map = common(subscription, periodNumber, amount);
            if (chargeId != null) {
                map.put("chargeId", chargeId.toString());
            }
            map.put("endsAtPeriodEnd", subscription.cancelAtPeriodEnd());
            return map;
        }
    }
}
