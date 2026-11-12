package dev.ngb.tenant.customer.application.customer.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read port for local customers; it selects straight into views. */
public interface CustomerReadRepository {

    List<CustomerView> list();

    Optional<CustomerView> byId(UUID id);

    Optional<CustomerView> byUserId(UUID userId);
}
