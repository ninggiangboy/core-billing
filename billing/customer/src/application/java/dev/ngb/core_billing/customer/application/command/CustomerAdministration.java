package dev.ngb.core_billing.customer.application.command;

import dev.ngb.core_billing.customer.domain.customer.Customer;
import dev.ngb.core_billing.platform.api.audit.AuditEntry;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.sharedkernel.Clock;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import dev.ngb.core_billing.customer.application.port.CustomerRepository;

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
