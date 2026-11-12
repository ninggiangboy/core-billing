package dev.ngb.sampletenant.resource.application.order.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read port for orders; it selects straight into views. */
public interface OrderReadRepository {

    List<OrderView> forCustomer(UUID customerId);

    List<OrderView> forBillingCustomer(String billingCustomerId);

    List<OrderView> all();

    Optional<OrderView> byId(UUID id);
}
