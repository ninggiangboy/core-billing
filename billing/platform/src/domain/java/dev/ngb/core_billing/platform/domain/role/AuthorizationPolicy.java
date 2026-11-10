package dev.ngb.core_billing.platform.domain.role;

import dev.ngb.core_billing.platform.api.actor.Permission;
import dev.ngb.core_billing.platform.api.actor.PermissionScope;
import java.util.Set;

/** Decides whether a set of granted permissions allows an action in a scope. */
public final class AuthorizationPolicy {

    private AuthorizationPolicy() {}

    /**
     * A permission applies only in the scopes it is defined for, so a Tenant-scope right can never
     * authorise a Platform action and the other way round, whatever a role happens to contain.
     */
    public static boolean permits(Set<Permission> granted, Permission required, PermissionScope scope) {
        return required.validIn(scope) && granted.contains(required);
    }
}
