package dev.ngb.core_billing.customer.application.query;

import dev.ngb.core_billing.customer.application.port.CustomerRepository;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.platform.api.tenant.TenantSystemId;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
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
