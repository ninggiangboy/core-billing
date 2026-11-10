package dev.ngb.core_billing.platform.api.actor;

public enum ActorType {
    /** A Billing User signed in through Keycloak. */
    USER,
    /** A Tenant System calling with a Billing-issued API credential. */
    TENANT_SYSTEM
}
