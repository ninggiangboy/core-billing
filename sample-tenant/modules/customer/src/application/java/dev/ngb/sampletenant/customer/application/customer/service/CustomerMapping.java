package dev.ngb.sampletenant.customer.application.customer.service;

import dev.ngb.sampletenant.billing.api.client.BillingCustomers;
import dev.ngb.sampletenant.billing.api.model.Customer;
import dev.ngb.sampletenant.customer.application.customer.port.CustomerRepository;
import dev.ngb.sampletenant.shared.error.BadRequestException;
import dev.ngb.sampletenant.shared.error.ConflictException;

/** Creates the Billing Customer and stores the returned id as the mapping. */
public class CustomerMapping {

    private final CustomerRepository customers;
    private final BillingCustomers billing;
    private final String tenantSystemId;

    public CustomerMapping(CustomerRepository customers, BillingCustomers billing, String tenantSystemId) {
        this.customers = customers;
        this.billing = billing;
        this.tenantSystemId = tenantSystemId;
    }

    public dev.ngb.sampletenant.customer.domain.customer.Customer map(dev.ngb.sampletenant.customer.domain.customer.Customer customer) {
        if (tenantSystemId == null || tenantSystemId.isBlank()) {
            throw new BadRequestException("sampletenant.billing.tenant-system-id is not configured");
        }
        String externalUserId = customer.userId().toString();
        try {
            Customer created = billing.create(
                    "customer:" + externalUserId,
                    new BillingCustomers.NewCustomer(tenantSystemId, externalUserId, customer.displayName(), customer.email()));
            return store(customer, created.id());
        } catch (ConflictException conflict) {
            // A mapping already exists under another key; the external-user lookup recovers it.
            Customer existing = billing.byExternalUser(externalUserId, tenantSystemId);
            return store(customer, existing.id());
        }
    }

    private dev.ngb.sampletenant.customer.domain.customer.Customer store(
            dev.ngb.sampletenant.customer.domain.customer.Customer customer, String billingCustomerId) {
        var mapped = customer.mapped(billingCustomerId);
        customers.save(mapped);
        return mapped;
    }
}
