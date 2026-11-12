package dev.ngb.billing.platform.api.actor;

import java.util.Arrays;
import java.util.Optional;

/**
 * The catalog of rights, defined in code and seeded into the database. Roles and users are granted
 * entries from this list; the list itself is never edited at runtime.
 */
public enum Permission {
    TENANT_CREATE("tenant:create", true, false, false),
    TENANT_READ("tenant:read", true, false, false),
    TENANT_DEACTIVATE("tenant:deactivate", true, false, false),
    TENANT_DOMAIN_MANAGE("tenant-domain:manage", true, false, false),

    USER_READ("user:read", true, true, false),
    USER_MANAGE("user:manage", true, true, false),
    ROLE_READ("role:read", true, true, false),
    ROLE_MANAGE("role:manage", true, true, false),

    TENANT_SYSTEM_READ("tenant-system:read", false, true, false),
    TENANT_SYSTEM_MANAGE("tenant-system:manage", false, true, false),
    CREDENTIAL_MANAGE("credential:manage", false, true, false),

    CUSTOMER_READ("customer:read", false, true, true),
    CUSTOMER_WRITE("customer:write", false, true, true),

    /** Creates and revokes a Billing Session for one Customer. A Tenant System may hold it. */
    BILLING_SESSION_CREATE("billing-session:create", false, true, true),
    /** Manages the return URLs a Billing Session may point back to. Administrators only. */
    PORTAL_SETTINGS_MANAGE("portal-settings:manage", false, true, false),

    WALLET_READ("wallet:read", false, true, true),
    WALLET_MANAGE("wallet:manage", false, true, true),
    RESERVATION_MANAGE("reservation:manage", false, true, true),
    CHARGE_CREATE("charge:create", false, true, true),
    /** Corrects the ledger. Administrators only: never granted to a Tenant System. */
    LEDGER_REVERSE("ledger:reverse", false, true, false),

    WEBHOOK_READ("webhook:read", false, true, true),
    WEBHOOK_MANAGE("webhook:manage", false, true, true),

    PRICING_READ("pricing:read", false, true, true),
    /** Creates, versions and retires pricing plans. Administrators only: a plan sets what customers are charged. */
    PRICING_MANAGE("pricing:manage", false, true, false),
    USAGE_RECORD("usage:record", false, true, true),
    USAGE_READ("usage:read", false, true, true),

    /** Reads the Stripe configuration (never its secrets), webhook processing history and reconciliation findings. */
    STRIPE_READ("stripe:read", false, true, false),
    /** Connects or disconnects Stripe, sets its secrets, retries events and runs reconciliation. Administrators only. */
    STRIPE_MANAGE("stripe:manage", false, true, false),
    PAYMENT_READ("payment:read", false, true, true),
    /** Starts a wallet top-up through the Tenant's Stripe configuration. */
    PAYMENT_CREATE("payment:create", false, true, true),
    /** Sends money back to a customer's card. Administrators only: never granted to a Tenant System. */
    REFUND_CREATE("refund:create", false, true, false),

    INVOICE_READ("invoice:read", false, true, true),
    /** Asks for an invoice for one charge. */
    INVOICE_CREATE("invoice:create", false, true, true),
    /** Voids an invoice or writes it off as uncollectible. Administrators only: it changes what a customer is asked to pay. */
    INVOICE_MANAGE("invoice:manage", false, true, false),

    SUBSCRIPTION_READ("subscription:read", false, true, true),
    /** Starts and cancels subscriptions. */
    SUBSCRIPTION_MANAGE("subscription:manage", false, true, true);

    private final String code;
    private final boolean platform;
    private final boolean tenant;
    private final boolean tenantSystem;

    Permission(String code, boolean platform, boolean tenant, boolean tenantSystem) {
        this.code = code;
        this.platform = platform;
        this.tenant = tenant;
        this.tenantSystem = tenantSystem;
    }

    public String code() {
        return code;
    }

    public boolean validIn(PermissionScope scope) {
        return scope == PermissionScope.PLATFORM ? platform : tenant;
    }

    /** True for rights an API credential may carry. */
    public boolean grantableToTenantSystem() {
        return tenantSystem;
    }

    public static Optional<Permission> fromCode(String code) {
        return Arrays.stream(values()).filter(p -> p.code.equals(code)).findFirst();
    }
}
