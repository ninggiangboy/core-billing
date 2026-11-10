package io.corebilling.customer.application.port;

import io.corebilling.customer.domain.customer.Customer;
import io.corebilling.customer.domain.customer.ExternalIdentity;
import io.corebilling.sharedkernel.CustomerId;
import java.util.Optional;

/** Reads see only the current tenant's customers, because the database enforces it. */
public interface CustomerRepository {

    /** @throws io.corebilling.platform.api.error.ConflictException when an external user is already mapped to a customer */
    void add(Customer customer);

    void update(Customer customer);

    Optional<Customer> findById(CustomerId id);

    Optional<Customer> findByExternalIdentity(ExternalIdentity identity);
}
