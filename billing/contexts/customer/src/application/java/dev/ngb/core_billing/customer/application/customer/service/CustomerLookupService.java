package dev.ngb.core_billing.customer.application.customer.service;

import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.core_billing.customer.api.lookup.CustomerLookup;
import dev.ngb.core_billing.customer.api.lookup.CustomerSummary;
import dev.ngb.core_billing.customer.application.customer.query.CustomerReadRepository;
import dev.ngb.core_billing.sharedkernel.id.CustomerId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    @Override
    public Optional<CustomerSummary> findFor(CustomerId id, UUID tenantSystem) {
        if (tenantSystem == null) {
            return find(id);
        }
        return unitOfWork.transactional(() -> customers.findByIdMappedTo(id, tenantSystem)
                .map(c -> new CustomerSummary(c.id(), c.displayName(), c.isActive())));
    }

    @Override
    public List<CustomerId> mappedTo(UUID tenantSystem) {
        return unitOfWork.transactional(() -> customers.mappedCustomerIds(tenantSystem).stream().map(CustomerId::new).toList());
    }
}
