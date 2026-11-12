package dev.ngb.sampletenant.resource.application.order.command;

import dev.ngb.core.runtime.Clock;
import dev.ngb.sampletenant.billing.api.client.BillingSubscriptions;
import dev.ngb.sampletenant.resource.application.order.port.OrderRepository;
import dev.ngb.sampletenant.resource.application.order.query.OrderQueries;
import dev.ngb.sampletenant.resource.domain.order.Order;
import dev.ngb.sampletenant.shared.error.BadRequestException;
import java.util.UUID;

/** Order changes that reach Billing: cancelling a subscription-backed order keeps its paid period. */
public class ManageOrderUseCase {

    private final OrderQueries queries;
    private final OrderRepository orders;
    private final BillingSubscriptions subscriptions;
    private final Clock clock;

    public ManageOrderUseCase(
            OrderQueries queries, OrderRepository orders, BillingSubscriptions subscriptions, Clock clock) {
        this.queries = queries;
        this.orders = orders;
        this.subscriptions = subscriptions;
        this.clock = clock;
    }

    public Order cancel(UUID orderId, UUID userId, boolean administrator) {
        Order order = queries.forUser(orderId, userId, administrator);
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
