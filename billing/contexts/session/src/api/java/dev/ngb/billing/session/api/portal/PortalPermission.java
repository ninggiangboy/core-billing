package dev.ngb.billing.session.api.portal;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a Billing Session may do in the Customer Portal. The set is chosen when the Tenant System creates the
 * session and is carried by the portal principal; it is never widened afterwards.
 */
public enum PortalPermission {
    /** Every session has this: it may read its own customer, permissions and expiry. */
    SESSION_VIEW("session:view"),
    CUSTOMER_VIEW("customer:view"),
    WALLET_VIEW("wallet:view"),
    LEDGER_VIEW("ledger:view"),
    USAGE_VIEW("usage:view"),
    CHARGE_VIEW("charge:view"),
    INVOICE_VIEW("invoice:view"),
    SUBSCRIPTION_VIEW("subscription:view"),
    PAYMENT_VIEW("payment:view"),
    REFUND_VIEW("refund:view"),
    TOP_UP("top-up:create"),
    PAYMENT_METHOD_MANAGE("payment-method:manage");

    private final String code;

    PortalPermission(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** The permissions a session gets when its creator names none: every read, no money movement. */
    public static Set<PortalPermission> defaultReads() {
        return Set.of(SESSION_VIEW, CUSTOMER_VIEW, WALLET_VIEW, LEDGER_VIEW, USAGE_VIEW, CHARGE_VIEW, INVOICE_VIEW, SUBSCRIPTION_VIEW, PAYMENT_VIEW,
                REFUND_VIEW);
    }

    public static Set<PortalPermission> fromCodes(Set<String> codes) {
        return codes.stream().map(PortalPermission::fromCode).collect(Collectors.toUnmodifiableSet());
    }

    /** @throws IllegalArgumentException when the code is not in the catalog */
    public static PortalPermission fromCode(String code) {
        return Arrays.stream(values())
                .filter(permission -> permission.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown portal permission: " + code));
    }
}
