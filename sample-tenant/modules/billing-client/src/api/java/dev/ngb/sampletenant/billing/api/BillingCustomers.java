package dev.ngb.sampletenant.billing.api;

import dev.ngb.sampletenant.billing.api.model.Customer;

/** The customer operations a Tenant System may call. */
public interface BillingCustomers {

    record NewCustomer(String tenantSystemId, String externalUserId, String displayName, String email) {}

    Customer create(String idempotencyKey, NewCustomer customer);

    Customer get(String id);

    Customer byExternalUser(String externalUserId, String tenantSystemId);
}
