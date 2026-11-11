package dev.ngb.sampletenant.customer.application;

import dev.ngb.sampletenant.customer.api.CustomerDirectory;
import dev.ngb.sampletenant.customer.application.port.CustomerRepository;
import dev.ngb.sampletenant.customer.domain.Customer;
import dev.ngb.sampletenant.shared.error.NotFoundException;
import java.util.List;
import java.util.UUID;

/** Read side of the customer mapping. */
public class CustomerQueries implements CustomerDirectory {

    private final CustomerRepository customers;

    public CustomerQueries(CustomerRepository customers) {
        this.customers = customers;
    }

    @Override
    public CustomerSnapshot byUserId(UUID userId) {
        return snapshot(byUser(userId));
    }

    public Customer byUser(UUID userId) {
        return customers.findByUserId(userId).orElseThrow(() -> new NotFoundException("customer not found"));
    }

    public Customer byId(UUID id) {
        return customers.findById(id).orElseThrow(() -> new NotFoundException("customer not found"));
    }

    public List<Customer> list() {
        return customers.findAll();
    }

    private static CustomerSnapshot snapshot(Customer customer) {
        return new CustomerSnapshot(
                customer.id(), customer.userId(), customer.billingCustomerId(), customer.mapped());
    }
}
