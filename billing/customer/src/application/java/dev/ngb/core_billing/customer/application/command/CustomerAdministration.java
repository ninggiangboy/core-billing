package dev.ngb.core_billing.customer.application.command;

import dev.ngb.core_billing.customer.domain.customer.Customer;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.platform.api.audit.AuditEntry;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.platform.api.tenant.TenantSystemId;
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

    public Customer update(Audit audit, CustomerId id, String displayName, String email) {
        return update(audit, id, displayName, email, null);
    }

    public Customer update(Audit audit, CustomerId id, String displayName, String email, TenantSystemId caller) {
        return unitOfWork.transactional(() -> {
            Customer customer = requireVisible(id, caller);
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

    public Customer deactivate(Audit audit, CustomerId id) {
        return deactivate(audit, id, null);
    }

    public Customer deactivate(Audit audit, CustomerId id, TenantSystemId caller) {
        return unitOfWork.transactional(() -> {
            Customer customer = requireVisible(id, caller);
            customer.deactivate();
            customers.update(customer);
            record(audit, "customer.deactivated", customer);
            return customer;
        });
    }

    /** A Tenant System may change only a customer it mapped; anything else is not found for it. */
    private Customer requireVisible(CustomerId id, TenantSystemId caller) {
        if (caller != null && !customers.isMappedTo(id, caller)) {
            throw new NotFoundException("customer not found");
        }
        return customers.findById(id).orElseThrow(() -> new NotFoundException("customer not found"));
    }

    private void record(Audit audit, String action, Customer customer) {
        auditLog.record(AuditEntry.tenant(
                tenantContext.requireTenant(), audit.actor(), action, "customer/" + customer.id(), audit.requestId(), clock.now()));
    }
}
