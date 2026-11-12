package dev.ngb.billing.payment.domain.stripe;

public enum IntegrationMode {
    /** The Tenant gives Billing its own Stripe API key. */
    DIRECT,
    /** The Tenant links a Stripe account through the Platform's Connect application; Billing holds no key of the Tenant's. */
    CONNECT
}
