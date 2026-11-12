package dev.ngb.tenant.resource.application.order.query;

import dev.ngb.tenant.customer.api.lookup.CustomerDirectory;
import dev.ngb.tenant.shared.error.NotFoundException;
import java.util.List;
import java.util.UUID;

/** Read side of orders, scoped to the caller's customer. */
public class OrderQueries {

    private final OrderReadRepository orders;
    private final CustomerDirectory customers;

    public OrderQueries(OrderReadRepository orders, CustomerDirectory customers) {
        this.orders = orders;
        this.customers = customers;
    }

    public List<OrderView> forUser(UUID userId) {
        return orders.forCustomer(customers.byUserId(userId).id());
    }

    public List<OrderView> all() {
        return orders.all();
    }

    public OrderView forUser(UUID orderId, UUID userId, boolean administrator) {
        OrderView order = orders.byId(orderId).orElseThrow(() -> new NotFoundException("order not found"));
        if (!administrator && !order.customerId().equals(customers.byUserId(userId).id())) {
            throw new NotFoundException("order not found");
        }
        return order;
    }
}
