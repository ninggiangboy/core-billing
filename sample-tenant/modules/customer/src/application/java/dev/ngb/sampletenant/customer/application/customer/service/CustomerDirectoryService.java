package dev.ngb.sampletenant.customer.application.customer.service;

import dev.ngb.sampletenant.customer.api.lookup.CustomerDirectory;
import dev.ngb.sampletenant.customer.application.customer.query.CustomerReadRepository;
import dev.ngb.sampletenant.customer.application.customer.query.CustomerView;
import dev.ngb.sampletenant.shared.error.NotFoundException;
import java.util.UUID;

/** Serves the customer facts other modules need, through the query side. */
public class CustomerDirectoryService implements CustomerDirectory {

    private final CustomerReadRepository customers;

    public CustomerDirectoryService(CustomerReadRepository customers) {
        this.customers = customers;
    }

    @Override
    public CustomerSnapshot byUserId(UUID userId) {
        CustomerView customer = customers.byUserId(userId)
                .orElseThrow(() -> new NotFoundException("customer not found"));
        return new CustomerSnapshot(
                customer.id(), customer.userId(), customer.billingCustomerId(), customer.mapped());
    }
}
