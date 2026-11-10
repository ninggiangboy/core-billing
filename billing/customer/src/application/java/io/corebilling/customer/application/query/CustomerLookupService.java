package io.corebilling.customer.application.query;

import io.corebilling.customer.api.lookup.CustomerLookup;
import io.corebilling.customer.api.lookup.CustomerSummary;
import io.corebilling.sharedkernel.CustomerId;
import io.corebilling.sharedkernel.UnitOfWork;
import java.util.Optional;
import io.corebilling.customer.application.query.CustomerReadRepository;

public class CustomerLookupService implements CustomerLookup {

    private final UnitOfWork unitOfWork;
    private final CustomerReadRepository customers;

    public CustomerLookupService(UnitOfWork unitOfWork, CustomerReadRepository customers) {
        this.unitOfWork = unitOfWork;
        this.customers = customers;
    }

    @Override
    public Optional<CustomerSummary> find(CustomerId id) {
        return unitOfWork.transactional(
                () -> customers.findById(id).map(c -> new CustomerSummary(c.id(), c.displayName(), c.isActive())));
    }
}
