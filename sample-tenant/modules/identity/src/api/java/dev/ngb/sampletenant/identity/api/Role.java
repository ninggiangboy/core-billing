package dev.ngb.sampletenant.identity.api;

import java.util.EnumSet;
import java.util.Set;

/** A business role. The sample tenant ships three fixed roles; Billing's roles are unrelated. */
public enum Role {
    ADMIN(EnumSet.allOf(Permission.class)),
    OPERATOR(EnumSet.of(
            Permission.ORDER_CREATE,
            Permission.ORDER_MANAGE,
            Permission.ORDER_READ,
            Permission.USAGE_RECORD,
            Permission.USAGE_READ,
            Permission.BILLING_READ,
            Permission.TOPUP_CREATE)),
    VIEWER(EnumSet.of(Permission.ORDER_READ, Permission.USAGE_READ, Permission.BILLING_READ));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = Set.copyOf(permissions);
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    public boolean permits(Permission permission) {
        return permissions.contains(permission);
    }
}
