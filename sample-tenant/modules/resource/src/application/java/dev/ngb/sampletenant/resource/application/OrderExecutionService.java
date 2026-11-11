package dev.ngb.sampletenant.resource.application;

import dev.ngb.sampletenant.resource.api.ExecutionOrders;
import dev.ngb.sampletenant.resource.application.port.OrderRepository;
import dev.ngb.sampletenant.resource.domain.Order;
import dev.ngb.sampletenant.resource.domain.OrderStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Applies worker and webhook-driven state changes to orders. */
public class OrderExecutionService implements ExecutionOrders {

    private final OrderRepository orders;
    private final Clock clock;

    public OrderExecutionService(OrderRepository orders, Clock clock) {
        this.orders = orders;
        this.clock = clock;
    }

    @Override
    public Optional<ExecutionOrder> find(UUID orderId) {
        return orders.findById(orderId).map(OrderExecutionService::snapshot);
    }

    @Override
    public void markRunning(UUID orderId) {
        save(order(orderId).running(clock.instant()));
    }

    @Override
    public void markQueued(UUID orderId) {
        save(order(orderId).resumed(clock.instant()));
    }

    @Override
    public void markCompleted(UUID orderId) {
        save(order(orderId).completed(clock.instant()));
    }

    @Override
    public void markFailed(UUID orderId, String reason) {
        save(order(orderId).failed(reason, clock.instant()));
    }

    @Override
    public int suspendByBillingCustomer(String billingCustomerId, Instant now) {
        int suspended = 0;
        for (Order order : orders.findByBillingCustomerId(billingCustomerId)) {
            if (order.status() == OrderStatus.QUEUED || order.status() == OrderStatus.RUNNING) {
                save(order.suspended(now));
                suspended++;
            }
        }
        return suspended;
    }

    @Override
    public int resumeByBillingCustomer(String billingCustomerId, Instant now) {
        int resumed = 0;
        for (Order order : orders.findByBillingCustomerId(billingCustomerId)) {
            if (order.status() == OrderStatus.SUSPENDED) {
                save(order.resumed(now));
                resumed++;
            }
        }
        return resumed;
    }

    private Order order(UUID orderId) {
        return orders.findById(orderId).orElseThrow(() -> new IllegalStateException("order not found: " + orderId));
    }

    private void save(Order order) {
        orders.save(order);
    }

    private static ExecutionOrder snapshot(Order order) {
        return new ExecutionOrder(
                order.id(),
                order.customerId(),
                order.billingCustomerId(),
                order.walletId(),
                order.billingPlanId(),
                order.pricingModel(),
                order.metric(),
                order.usageQuantity(),
                order.unit(),
                order.eventCount(),
                order.estimatedMinorUnits(),
                order.currency(),
                order.reservationId(),
                order.subscriptionId(),
                order.reference(),
                order.status().name());
    }
}
