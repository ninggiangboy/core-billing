package dev.ngb.core_billing.session.domain.portal;

/** A Billing Session is active until it expires or the Tenant System (or a Tenant User) revokes it. */
public enum SessionStatus {
    ACTIVE,
    REVOKED
}
