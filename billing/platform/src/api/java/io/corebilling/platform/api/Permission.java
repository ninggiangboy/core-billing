package io.corebilling.platform.api;

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
    USAGE_READ("usage:read", false, true, true);

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
