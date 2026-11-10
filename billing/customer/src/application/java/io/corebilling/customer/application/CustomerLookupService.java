package io.corebilling.customer.application;

import io.corebilling.customer.api.CustomerLookup;
import io.corebilling.customer.api.CustomerSummary;
import io.corebilling.sharedkernel.CustomerId;
import io.corebilling.sharedkernel.UnitOfWork;
import java.util.Optional;

public class CustomerLookupService implements CustomerLookup {

    private final UnitOfWork unitOfWork;
    private final CustomerRepository customers;

    public CustomerLookupService(UnitOfWork unitOfWork, CustomerRepository customers) {
        this.unitOfWork = unitOfWork;
        this.customers = customers;
    }

    @Override
    public Optional<CustomerSummary> find(CustomerId id) {
        return unitOfWork.transactional(
                () -> customers.findById(id).map(c -> new CustomerSummary(c.id(), c.displayName(), c.isActive())));
    }
}
