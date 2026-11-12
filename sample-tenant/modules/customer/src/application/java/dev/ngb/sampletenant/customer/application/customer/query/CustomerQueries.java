package dev.ngb.sampletenant.customer.application.customer.query;

import dev.ngb.sampletenant.customer.application.customer.port.CustomerRepository;
import dev.ngb.sampletenant.customer.domain.customer.Customer;
import dev.ngb.sampletenant.shared.error.NotFoundException;
import java.util.List;
import java.util.UUID;

/** Read side of the customer mapping. */
public class CustomerQueries {

    private final CustomerRepository customers;

    public CustomerQueries(CustomerRepository customers) {
        this.customers = customers;
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
}
