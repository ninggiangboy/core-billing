package io.corebilling.customer.application;

import io.corebilling.customer.domain.Customer;
import io.corebilling.customer.domain.ExternalIdentity;
import io.corebilling.sharedkernel.CustomerId;
import java.util.List;
import java.util.Optional;

/** Reads see only the current tenant's customers, because the database enforces it. */
public interface CustomerRepository {

    /** @throws io.corebilling.platform.api.ConflictException when an external user is already mapped to a customer */
    void add(Customer customer);

    void update(Customer customer);

    Optional<Customer> findById(CustomerId id);

    Optional<Customer> findByExternalIdentity(ExternalIdentity identity);

    List<Customer> list();
}
