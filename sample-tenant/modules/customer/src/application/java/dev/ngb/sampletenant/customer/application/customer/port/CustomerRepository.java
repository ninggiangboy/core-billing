package dev.ngb.sampletenant.customer.application.customer.port;

import dev.ngb.sampletenant.customer.domain.customer.Customer;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for local customers and their Billing ids. */
public interface CustomerRepository {

    void save(Customer customer);

    Optional<Customer> findById(UUID id);

    Optional<Customer> findByUserId(UUID userId);
}
