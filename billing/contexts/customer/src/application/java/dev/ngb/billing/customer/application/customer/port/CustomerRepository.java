package dev.ngb.billing.customer.application.customer.port;

import dev.ngb.billing.customer.domain.customer.Customer;
import dev.ngb.billing.customer.domain.customer.ExternalIdentity;
import dev.ngb.billing.platform.api.tenant.TenantSystemId;
import dev.ngb.billing.sharedkernel.id.CustomerId;
import java.util.Optional;

/** Reads see only the current tenant's customers, because the database enforces it. */
public interface CustomerRepository {

    /**
     * Inserts the customer unless this tenant already has one with its idempotency key.
     *
     * @return true when the row was inserted, false when the key already existed
     * @throws dev.ngb.billing.platform.api.error.ConflictException when an external user is already mapped to a customer
     */
    boolean addIfAbsent(Customer customer);

    void update(Customer customer);

    Optional<Customer> findById(CustomerId id);

    Optional<Customer> findByIdempotencyKey(String idempotencyKey);

    Optional<Customer> findByExternalIdentity(ExternalIdentity identity);

    boolean isMappedTo(CustomerId id, TenantSystemId tenantSystem);
}
