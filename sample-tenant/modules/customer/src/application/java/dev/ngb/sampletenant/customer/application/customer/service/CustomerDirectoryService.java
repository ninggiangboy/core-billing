package dev.ngb.sampletenant.customer.application.customer.service;

import dev.ngb.sampletenant.customer.api.lookup.CustomerDirectory;
import dev.ngb.sampletenant.customer.application.customer.query.CustomerQueries;
import dev.ngb.sampletenant.customer.domain.customer.Customer;
import java.util.UUID;

/** Serves the customer facts other modules need, through the query side. */
public class CustomerDirectoryService implements CustomerDirectory {

    private final CustomerQueries customers;

    public CustomerDirectoryService(CustomerQueries customers) {
        this.customers = customers;
    }

    @Override
    public CustomerSnapshot byUserId(UUID userId) {
        Customer customer = customers.byUser(userId);
        return new CustomerSnapshot(
                customer.id(), customer.userId(), customer.billingCustomerId(), customer.mapped());
    }
}
