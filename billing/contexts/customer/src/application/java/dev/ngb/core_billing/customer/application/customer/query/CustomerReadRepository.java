package dev.ngb.core_billing.customer.application.customer.query;

import dev.ngb.core_billing.sharedkernel.id.CustomerId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only projections of the current tenant's customers. */
public interface CustomerReadRepository {

    List<CustomerView> list();

    /** Customers mapped to one Tenant System, with only that system's identity in the view. */
    List<CustomerView> listMappedTo(UUID tenantSystemId);

    Optional<CustomerView> findById(CustomerId id);

    /** The customer only when it is mapped to the system, with only that system's identity in the view. */
    Optional<CustomerView> findByIdMappedTo(CustomerId id, UUID tenantSystemId);

    Optional<CustomerView> findByExternalIdentity(UUID tenantSystemId, String externalUserId);

    List<UUID> mappedCustomerIds(UUID tenantSystemId);
}
