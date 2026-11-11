package dev.ngb.sampletenant.resource.api;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** The order operations the execution worker and the webhook policy need. */
public interface ExecutionOrders {

    record ExecutionOrder(
            UUID id,
            UUID customerId,
            String billingCustomerId,
            String walletId,
            String billingPlanId,
            String pricingModel,
            String metric,
            String usageQuantity,
            String unit,
            Long eventCount,
            long estimatedMinorUnits,
            String currency,
            String reservationId,
            String reference,
            String status) {}

    Optional<ExecutionOrder> find(UUID orderId);

    void markRunning(UUID orderId);

    void markQueued(UUID orderId);

    void markCompleted(UUID orderId);

    void markFailed(UUID orderId, String reason);

    int suspendByBillingCustomer(String billingCustomerId, Instant now);

    int resumeByBillingCustomer(String billingCustomerId, Instant now);
}
