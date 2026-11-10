package io.corebilling.customer.application.query;

import io.corebilling.customer.application.port.CustomerRepository;
import io.corebilling.platform.api.error.NotFoundException;
import io.corebilling.platform.api.tenant.TenantSystemId;
import io.corebilling.sharedkernel.CustomerId;
import io.corebilling.sharedkernel.UnitOfWork;
import java.util.List;

/** Read access to the current tenant's customers. */
public class CustomerQueries {

    private final UnitOfWork unitOfWork;
    private final CustomerReadRepository customers;

    public CustomerQueries(UnitOfWork unitOfWork, CustomerReadRepository customers) {
        this.unitOfWork = unitOfWork;
        this.customers = customers;
    }

    public List<CustomerView> list() {
        return unitOfWork.transactional(customers::list);
    }

    public CustomerView get(CustomerId id) {
        return unitOfWork.transactional(() -> customers.findById(id).orElseThrow(() -> new NotFoundException("customer not found")));
    }

    public CustomerView getByExternalUser(TenantSystemId tenantSystemId, String externalUserId) {
        return unitOfWork.transactional(() -> customers.findByExternalIdentity(tenantSystemId.value(), externalUserId)
                .orElseThrow(() -> new NotFoundException("customer not found")));
    }
}
