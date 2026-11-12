package dev.ngb.tenant.resource.application.order.command;

import dev.ngb.core.runtime.Clock;
import dev.ngb.tenant.billing.api.client.BillingSubscriptions;
import dev.ngb.tenant.customer.api.lookup.CustomerDirectory;
import dev.ngb.tenant.resource.application.order.port.OrderRepository;
import dev.ngb.tenant.resource.domain.order.Order;
import dev.ngb.tenant.shared.error.BadRequestException;
import dev.ngb.tenant.shared.error.NotFoundException;
import java.util.UUID;

/** Order changes that reach Billing: cancelling a subscription-backed order keeps its paid period. */
public class ManageOrderUseCase {

    private final OrderRepository orders;
    private final CustomerDirectory customers;
    private final BillingSubscriptions subscriptions;
    private final Clock clock;

    public ManageOrderUseCase(
            OrderRepository orders, CustomerDirectory customers, BillingSubscriptions subscriptions, Clock clock) {
        this.orders = orders;
        this.customers = customers;
        this.subscriptions = subscriptions;
        this.clock = clock;
    }

    public Order cancel(UUID orderId, UUID userId, boolean administrator) {
        Order order = orders.findById(orderId).orElseThrow(() -> new NotFoundException("order not found"));
        if (!administrator && !order.customerId().equals(customers.byUserId(userId).id())) {
            throw new NotFoundException("order not found");
        }
        if (order.terminal()) {
            throw new BadRequestException("this order is already " + order.status());
        }
        if (order.subscriptionId() != null) {
            subscriptions.cancel(order.subscriptionId());
        }
        Order canceled = order.canceled(clock.now());
        orders.save(canceled);
        return canceled;
    }
}
