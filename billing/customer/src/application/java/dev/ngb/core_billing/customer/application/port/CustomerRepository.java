package dev.ngb.core_billing.customer.application.port;

import dev.ngb.core_billing.customer.domain.customer.Customer;
import dev.ngb.core_billing.customer.domain.customer.ExternalIdentity;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import java.util.Optional;

/** Reads see only the current tenant's customers, because the database enforces it. */
public interface CustomerRepository {

    /** @throws dev.ngb.core_billing.platform.api.error.ConflictException when an external user is already mapped to a customer */
    void add(Customer customer);

    void update(Customer customer);

    Optional<Customer> findById(CustomerId id);

    Optional<Customer> findByExternalIdentity(ExternalIdentity identity);
}
