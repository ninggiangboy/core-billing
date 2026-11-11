package dev.ngb.core_billing.invoicing.domain.subscription;

import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One line of a subscription's history. It records what happened and, for a period, which period and how much; it is
 * append-only and never changes. The notices sent to the Tenant System are a separate thing ({@code SubscriptionNotices}).
 */
public record SubscriptionEvent(
        UUID id, TenantId tenantId, SubscriptionId subscriptionId, Type type, Integer periodNumber, Instant periodStart, Instant periodEnd, Money amount, UUID chargeId, String detail,
        Instant occurredAt) {

    public enum Type { CREATED, RENEWED, PAYMENT_FAILED, CANCEL_REQUESTED, CANCELED }

    public SubscriptionEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(subscriptionId, "subscriptionId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (detail != null && detail.length() > 500) {
            detail = detail.substring(0, 500);
        }
    }

    public static SubscriptionEvent created(UUID id, Subscription subscription, Instant now) {
        return new SubscriptionEvent(id, subscription.tenantId(), subscription.id(), Type.CREATED, null, null, null, null, null, "plan " + subscription.planCode(), now);
    }

    public static SubscriptionEvent renewed(UUID id, Subscription subscription, int periodNumber, Money amount, UUID chargeId, Instant now) {
        return new SubscriptionEvent(id, subscription.tenantId(), subscription.id(), Type.RENEWED, periodNumber, subscription.periodStart(periodNumber),
                subscription.periodEnd(periodNumber), amount, chargeId, null, now);
    }

    public static SubscriptionEvent paymentFailed(UUID id, Subscription subscription, int periodNumber, Money amount, String reason, Instant now) {
        return new SubscriptionEvent(id, subscription.tenantId(), subscription.id(), Type.PAYMENT_FAILED, periodNumber, subscription.periodStart(periodNumber),
                subscription.periodEnd(periodNumber), amount, null, reason, now);
    }

    public static SubscriptionEvent cancelRequested(UUID id, Subscription subscription, Instant now) {
        return new SubscriptionEvent(id, subscription.tenantId(), subscription.id(), Type.CANCEL_REQUESTED, null, subscription.currentPeriodStart(), subscription.currentPeriodEnd(), null,
                null, "ends at the end of the paid period", now);
    }

    public static SubscriptionEvent canceled(UUID id, Subscription subscription, String detail, Instant now) {
        return new SubscriptionEvent(id, subscription.tenantId(), subscription.id(), Type.CANCELED, null, null, null, null, null, detail, now);
    }
}
