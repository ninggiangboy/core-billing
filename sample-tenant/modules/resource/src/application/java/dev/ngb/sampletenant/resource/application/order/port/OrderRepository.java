package dev.ngb.sampletenant.resource.application.order.port;

import dev.ngb.sampletenant.resource.domain.order.Order;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for orders. */
public interface OrderRepository {

    void save(Order order);

    Optional<Order> findById(UUID id);

    List<Order> findByBillingCustomerId(String billingCustomerId);
}
