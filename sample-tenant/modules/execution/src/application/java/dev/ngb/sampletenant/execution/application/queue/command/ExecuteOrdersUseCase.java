package dev.ngb.sampletenant.execution.application.queue.command;

import dev.ngb.core.runtime.Clock;
import dev.ngb.sampletenant.billing.api.client.BillingCharges;
import dev.ngb.sampletenant.billing.api.model.Charge;
import dev.ngb.sampletenant.billing.api.model.ChargeUsageRequest;
import dev.ngb.sampletenant.resource.api.execution.ExecutionOrders;
import dev.ngb.sampletenant.resource.api.execution.ExecutionQueue;
import dev.ngb.sampletenant.usage.api.recording.UsageRecorder;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Scenarios 5, 6 and 7: run a claimed order, record usage during the run, charge the cost from the
 * reservation, then release what is left. A failure before the charge is requeued up to a bound so
 * a Billing outage does not lose the order.
 */
public class ExecuteOrdersUseCase {

    public record Report(int claimed, int completed, int failed, int retried) {}

    private final ExecutionQueue queue;
    private final ExecutionOrders orders;
    private final UsageRecorder usage;
    private final BillingCharges charges;
    private final Clock clock;
    private final int maxAttempts;

    public ExecuteOrdersUseCase(
            ExecutionQueue queue,
            ExecutionOrders orders,
            UsageRecorder usage,
            BillingCharges charges,
            Clock clock,
            int maxAttempts) {
        this.queue = queue;
        this.orders = orders;
        this.usage = usage;
        this.charges = charges;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
    }

    public Report run(int batchSize) {
        List<ExecutionQueue.Claimed> claimed = queue.claim(batchSize);
        int completed = 0;
        int failed = 0;
        int retried = 0;
        for (ExecutionQueue.Claimed item : claimed) {
            try {
                if (execute(item.orderId())) {
                    completed++;
                } else {
                    failed++;
                }
            } catch (RuntimeException e) {
                if (item.attempts() < maxAttempts) {
                    orders.markQueued(item.orderId());
                    queue.requeue(item.orderId());
                    retried++;
                } else {
                    orders.markFailed(item.orderId(), message(e));
                    queue.finish(item.orderId());
                    failed++;
                }
            }
        }
        return new Report(claimed.size(), completed, failed, retried);
    }

    private boolean execute(UUID orderId) {
        ExecutionOrders.ExecutionOrder order = orders.find(orderId).orElse(null);
        if (order == null || !"QUEUED".equals(order.status())) {
            queue.finish(orderId);
            return false;
        }
        orders.markRunning(orderId);
        if (!"SUBSCRIPTION".equals(order.pricingModel())) {
            usage.record(new UsageRecorder.UsageCommand(
                    order.id(),
                    order.billingCustomerId(),
                    order.metric(),
                    order.usageQuantity(),
                    order.unit(),
                    order.reference(),
                    clock.now()));
        }
        if ("HYBRID".equals(order.pricingModel())) {
            rateUsage(order);
        }

        Charge charge = charge(order);
        if (charge != null && "FAILED".equals(charge.status())) {
            orders.markFailed(orderId, charge.failureReason());
            release(order);
            queue.finish(orderId);
            return false;
        }
        release(order);
        orders.markCompleted(orderId);
        queue.finish(orderId);
        return true;
    }

    private void rateUsage(ExecutionOrders.ExecutionOrder order) {
        Instant to = clock.now();
        charges.chargeUsage(new ChargeUsageRequest(
                order.billingCustomerId(),
                order.walletId(),
                order.billingPlanId(),
                null,
                to.minusSeconds(86_400),
                to));
    }

    private Charge charge(ExecutionOrders.ExecutionOrder order) {
        if (order.estimatedMinorUnits() <= 0) {
            return null;
        }
        if (order.reservationId() != null) {
            return charges.capture(
                    "capture:" + order.id(),
                    order.reservationId(),
                    new BillingCharges.CaptureRequest(
                            order.estimatedMinorUnits(), order.currency(), order.reference()));
        }
        return charges.charge(
                "charge:" + order.id(),
                new BillingCharges.ChargeRequest(
                        order.walletId(), order.estimatedMinorUnits(), order.currency(), order.reference(), order.reference()));
    }

    private void release(ExecutionOrders.ExecutionOrder order) {
        if (order.reservationId() != null) {
            charges.release(order.reservationId());
        }
    }

    private static String message(RuntimeException e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return message.length() <= 300 ? message : message.substring(0, 300);
    }
}
