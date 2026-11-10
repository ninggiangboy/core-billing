package io.corebilling.customer.application;

import io.corebilling.customer.domain.Customer;
import io.corebilling.customer.domain.ExternalIdentity;
import io.corebilling.platform.api.AuditEntry;
import io.corebilling.platform.api.AuditLog;
import io.corebilling.platform.api.ConflictException;
import io.corebilling.platform.api.NotFoundException;
import io.corebilling.platform.api.TenantContext;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.CustomerId;
import io.corebilling.sharedkernel.UnitOfWork;
import java.util.List;

/** Reading and maintaining the Customers of the current tenant. */
public class CustomerAdministration {

    private final UnitOfWork unitOfWork;
    private final CustomerRepository customers;
    private final AuditLog auditLog;
    private final TenantContext tenantContext;
    private final Clock clock;

    public CustomerAdministration(
            UnitOfWork unitOfWork, CustomerRepository customers, AuditLog auditLog, TenantContext tenantContext, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.customers = customers;
        this.auditLog = auditLog;
        this.tenantContext = tenantContext;
        this.clock = clock;
    }

    public Customer get(CustomerId id) {
        return unitOfWork.transactional(() -> customers.findById(id).orElseThrow(() -> new NotFoundException("customer not found")));
    }

    public Customer getByExternalUser(ExternalIdentity identity) {
        return unitOfWork.transactional(
                () -> customers.findByExternalIdentity(identity).orElseThrow(() -> new NotFoundException("customer not found")));
    }

    public List<Customer> list() {
        return unitOfWork.transactional(customers::list);
    }

    public Customer update(AuditContext audit, CustomerId id, String displayName, String email) {
        return unitOfWork.transactional(() -> {
            Customer customer = customers.findById(id).orElseThrow(() -> new NotFoundException("customer not found"));
            try {
                customer.changeContact(displayName, email);
            } catch (IllegalStateException deactivated) {
                throw new ConflictException(deactivated.getMessage());
            }
            customers.update(customer);
            record(audit, "customer.updated", customer);
            return customer;
        });
    }

    public Customer deactivate(AuditContext audit, CustomerId id) {
        return unitOfWork.transactional(() -> {
            Customer customer = customers.findById(id).orElseThrow(() -> new NotFoundException("customer not found"));
            customer.deactivate();
            customers.update(customer);
            record(audit, "customer.deactivated", customer);
            return customer;
        });
    }

    private void record(AuditContext audit, String action, Customer customer) {
        auditLog.record(AuditEntry.tenant(
                tenantContext.requireTenant(), audit.actor(), action, "customer/" + customer.id(), audit.requestId(), clock.now()));
    }
}
