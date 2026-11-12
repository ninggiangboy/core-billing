package dev.ngb.tenant.customer.application.customer.port;

import dev.ngb.tenant.customer.domain.customer.Customer;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for local customers and their Billing ids. */
public interface CustomerRepository {

    void save(Customer customer);

    Optional<Customer> findById(UUID id);

    Optional<Customer> findByUserId(UUID userId);
}
