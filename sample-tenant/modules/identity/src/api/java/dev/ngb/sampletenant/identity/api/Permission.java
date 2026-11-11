package dev.ngb.sampletenant.identity.api;

/** The actions a business role may perform. A permission is checked per operation, never inferred from a URL. */
public enum Permission {
    USER_MANAGE("user:manage"),
    CUSTOMER_MANAGE("customer:manage"),
    CATALOG_MANAGE("catalog:manage"),
    ORDER_CREATE("order:create"),
    ORDER_MANAGE("order:manage"),
    ORDER_READ("order:read"),
    USAGE_RECORD("usage:record"),
    USAGE_READ("usage:read"),
    BILLING_READ("billing:read"),
    TOPUP_CREATE("top-up:create"),
    SETTINGS_MANAGE("settings:manage");

    private final String code;

    Permission(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
