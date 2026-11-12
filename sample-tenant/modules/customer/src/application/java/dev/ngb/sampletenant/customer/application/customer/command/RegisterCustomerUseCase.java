package dev.ngb.sampletenant.customer.application.customer.command;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.sampletenant.customer.application.customer.port.CustomerRepository;
import dev.ngb.sampletenant.customer.application.customer.service.CustomerMapping;
import dev.ngb.sampletenant.customer.domain.customer.Customer;
import dev.ngb.sampletenant.shared.error.NotFoundException;
import dev.ngb.sampletenant.shared.error.UpstreamUnavailableException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates the local customer at registration and tries the Billing mapping at once. Registration
 * still succeeds when Billing is down; the customer stays PENDING and can be mapped again.
 */
public class RegisterCustomerUseCase {

    private final CustomerRepository customers;
    private final CustomerMapping mapping;
    private final Clock clock;
    private final IdGenerator ids;

    public RegisterCustomerUseCase(CustomerRepository customers, CustomerMapping mapping, Clock clock, IdGenerator ids) {
        this.customers = customers;
        this.mapping = mapping;
        this.clock = clock;
        this.ids = ids;
    }

    public Customer register(UUID userId, String displayName, String email) {
        Optional<Customer> existing = customers.findByUserId(userId);
        if (existing.isPresent()) {
            return existing.get();
        }
        Customer pending = Customer.pending(ids, userId, displayName, email, clock.now());
        customers.save(pending);
        return tryMap(pending);
    }

    public Customer retry(UUID customerId) {
        Customer customer = customers.findById(customerId)
                .orElseThrow(() -> new NotFoundException("customer not found"));
        return mapping.map(customer);
    }

    private Customer tryMap(Customer customer) {
        try {
            return mapping.map(customer);
        } catch (UpstreamUnavailableException unavailable) {
            return customer;
        }
    }

    public List<Customer> list() {
        return customers.findAll();
    }
}
