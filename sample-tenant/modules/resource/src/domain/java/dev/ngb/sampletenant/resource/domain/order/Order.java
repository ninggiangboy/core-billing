package dev.ngb.sampletenant.resource.domain.order;

import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.sampletenant.shared.error.BadRequestException;
import java.time.Instant;
import java.util.UUID;

/**
 * One ordered resource. It records the Billing plan, wallet and reservation that were used, so the
 * execution worker settles exactly the hold this order created.
 */
public record Order(
        UUID id,
        UUID customerId,
        String billingCustomerId,
        String walletId,
        UUID catalogPlanId,
        String billingPlanId,
        String pricingModel,
        String metric,
        String usageQuantity,
        String unit,
        Long eventCount,
        long estimatedMinorUnits,
        String currency,
        String reservationId,
        String subscriptionId,
        OrderStatus status,
        String reference,
        String failureReason,
        Instant createdAt,
        Instant updatedAt) {

    public static Order queued(
            IdGenerator ids,
            UUID customerId,
            String billingCustomerId,
            String walletId,
            UUID catalogPlanId,
            String billingPlanId,
            String pricingModel,
            String metric,
            String usageQuantity,
            String unit,
            Long eventCount,
            long estimatedMinorUnits,
            String currency,
            String reservationId,
            String subscriptionId,
            Instant now) {
        UUID id = ids.newId();
        return new Order(
                id,
                customerId,
                billingCustomerId,
                walletId,
                catalogPlanId,
                billingPlanId,
                pricingModel,
                metric,
                usageQuantity,
                unit,
                eventCount,
                estimatedMinorUnits,
                currency,
                reservationId,
                subscriptionId,
                OrderStatus.QUEUED,
                "ORD-" + id,
                null,
                now,
                now);
    }

    public Order running(Instant now) {
        return transition(OrderStatus.RUNNING, failureReason, now);
    }

    public Order completed(Instant now) {
        return transition(OrderStatus.COMPLETED, failureReason, now);
    }

    public Order failed(String reason, Instant now) {
        return transition(OrderStatus.FAILED, reason, now);
    }

    public Order suspended(Instant now) {
        return transition(OrderStatus.SUSPENDED, failureReason, now);
    }

    public Order resumed(Instant now) {
        return transition(OrderStatus.QUEUED, failureReason, now);
    }

    public Order canceled(Instant now) {
        return transition(OrderStatus.CANCELED, failureReason, now);
    }

    public boolean terminal() {
        return status == OrderStatus.COMPLETED || status == OrderStatus.FAILED || status == OrderStatus.CANCELED;
    }

    private Order transition(OrderStatus next, String reason, Instant now) {
        if (terminal()) {
            throw new BadRequestException("order " + id + " is already " + status);
        }
        return new Order(
                id,
                customerId,
                billingCustomerId,
                walletId,
                catalogPlanId,
                billingPlanId,
                pricingModel,
                metric,
                usageQuantity,
                unit,
                eventCount,
                estimatedMinorUnits,
                currency,
                reservationId,
                subscriptionId,
                next,
                reference,
                reason,
                createdAt,
                now);
    }
}
