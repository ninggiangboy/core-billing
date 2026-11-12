package dev.ngb.tenant.customer.domain.customer;

/** Whether the local account already has a Billing Customer. */
public enum CustomerStatus {
    PENDING,
    MAPPED,
    DISABLED
}
