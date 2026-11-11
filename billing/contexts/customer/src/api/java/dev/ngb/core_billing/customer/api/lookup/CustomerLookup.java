package dev.ngb.core_billing.customer.api.lookup;

import dev.ngb.core_billing.sharedkernel.CustomerId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only lookup for the contexts that act on a Customer (money, payment, usage, pricing). It answers
 * within the current tenant scope, so another tenant's customer is simply absent.
 */
public interface CustomerLookup {

    Optional<CustomerSummary> find(CustomerId id);

    /** Empty unless the customer is mapped to {@code tenantSystem}; a null system answers like {@link #find}. */
    Optional<CustomerSummary> findFor(CustomerId id, UUID tenantSystem);

    /** The customers mapped to one Tenant System, within the current tenant. */
    List<CustomerId> mappedTo(UUID tenantSystem);
}
