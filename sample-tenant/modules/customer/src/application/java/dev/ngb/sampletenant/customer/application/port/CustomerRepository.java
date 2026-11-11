package dev.ngb.sampletenant.customer.application.port;

import dev.ngb.sampletenant.customer.domain.Customer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for local customers and their Billing ids. */
public interface CustomerRepository {

    void save(Customer customer);

    Optional<Customer> findById(UUID id);

    Optional<Customer> findByUserId(UUID userId);

    List<Customer> findAll();
}
