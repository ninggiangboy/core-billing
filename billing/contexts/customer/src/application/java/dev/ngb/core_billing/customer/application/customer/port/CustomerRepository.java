package dev.ngb.core_billing.customer.application.customer.port;

import dev.ngb.core_billing.customer.domain.customer.Customer;
import dev.ngb.core_billing.customer.domain.customer.ExternalIdentity;
import dev.ngb.core_billing.platform.api.tenant.TenantSystemId;
import dev.ngb.core_billing.sharedkernel.id.CustomerId;
import java.util.Optional;

/** Reads see only the current tenant's customers, because the database enforces it. */
public interface CustomerRepository {

    /**
     * Inserts the customer unless this tenant already has one with its idempotency key.
     *
     * @return true when the row was inserted, false when the key already existed
     * @throws dev.ngb.core_billing.platform.api.error.ConflictException when an external user is already mapped to a customer
     */
    boolean addIfAbsent(Customer customer);

    void update(Customer customer);

    Optional<Customer> findById(CustomerId id);

    Optional<Customer> findByIdempotencyKey(String idempotencyKey);

    Optional<Customer> findByExternalIdentity(ExternalIdentity identity);

    boolean isMappedTo(CustomerId id, TenantSystemId tenantSystem);
}
