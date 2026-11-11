package dev.ngb.core_billing.customer.application.customer.query;

import dev.ngb.core_billing.customer.application.customer.port.CustomerRepository;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.platform.api.tenant.TenantSystemId;
import dev.ngb.core_billing.sharedkernel.id.CustomerId;
import dev.ngb.core_billing.sharedkernel.runtime.UnitOfWork;
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
        return list(null);
    }

    /** A Tenant System sees only the customers mapped to it; any other caller sees the whole tenant. */
    public List<CustomerView> list(TenantSystemId caller) {
        return unitOfWork.transactional(() -> caller == null ? customers.list() : customers.listMappedTo(caller.value()));
    }

    public CustomerView get(CustomerId id) {
        return get(id, null);
    }

    public CustomerView get(CustomerId id, TenantSystemId caller) {
        return unitOfWork.transactional(() -> (caller == null ? customers.findById(id) : customers.findByIdMappedTo(id, caller.value()))
                .orElseThrow(() -> new NotFoundException("customer not found")));
    }

    public CustomerView getByExternalUser(TenantSystemId tenantSystemId, String externalUserId) {
        return unitOfWork.transactional(() -> customers.findByExternalIdentity(tenantSystemId.value(), externalUserId)
                .orElseThrow(() -> new NotFoundException("customer not found")));
    }
}
