package dev.ngb.sampletenant.customer.application.customer.query;

import java.util.List;
import java.util.UUID;

/** Read side of the customer mapping. */
public class CustomerQueries {

    private final CustomerReadRepository customers;

    public CustomerQueries(CustomerReadRepository customers) {
        this.customers = customers;
    }

    public CustomerView byUser(UUID userId) {
        return customers.byUserId(userId)
                .orElseThrow(() -> new dev.ngb.sampletenant.shared.error.NotFoundException("customer not found"));
    }

    public CustomerView byId(UUID id) {
        return customers.byId(id)
                .orElseThrow(() -> new dev.ngb.sampletenant.shared.error.NotFoundException("customer not found"));
    }

    public List<CustomerView> list() {
        return customers.list();
    }
}
