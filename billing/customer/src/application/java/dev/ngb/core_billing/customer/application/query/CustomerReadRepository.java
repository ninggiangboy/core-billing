package dev.ngb.core_billing.customer.application.query;

import dev.ngb.core_billing.sharedkernel.CustomerId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only projections of the current tenant's customers. */
public interface CustomerReadRepository {

    List<CustomerView> list();

    Optional<CustomerView> findById(CustomerId id);

    Optional<CustomerView> findByExternalIdentity(UUID tenantSystemId, String externalUserId);
}
