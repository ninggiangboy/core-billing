package dev.ngb.sampletenant.resource.application;

import dev.ngb.sampletenant.customer.api.lookup.CustomerDirectory;
import dev.ngb.sampletenant.resource.application.port.OrderRepository;
import dev.ngb.sampletenant.resource.domain.Order;
import dev.ngb.sampletenant.shared.error.NotFoundException;
import java.util.List;
import java.util.UUID;

/** Read side of orders, scoped to the caller's customer. */
public class OrderQueries {

    private final OrderRepository orders;
    private final CustomerDirectory customers;

    public OrderQueries(OrderRepository orders, CustomerDirectory customers) {
        this.orders = orders;
        this.customers = customers;
    }

    public List<Order> forUser(UUID userId) {
        return orders.findByCustomerId(customers.byUserId(userId).id());
    }

    public List<Order> all() {
        return orders.findAll();
    }

    public Order forUser(UUID orderId, UUID userId, boolean administrator) {
        Order order = orders.findById(orderId).orElseThrow(() -> new NotFoundException("order not found"));
        if (!administrator && !order.customerId().equals(customers.byUserId(userId).id())) {
            throw new NotFoundException("order not found");
        }
        return order;
    }

    public Order get(UUID orderId) {
        return orders.findById(orderId).orElseThrow(() -> new NotFoundException("order not found"));
    }
}
