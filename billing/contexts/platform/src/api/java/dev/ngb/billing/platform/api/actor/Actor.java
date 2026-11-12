package dev.ngb.billing.platform.api.actor;

import dev.ngb.core.id.TenantId;
import dev.ngb.billing.platform.api.tenant.TenantSystemId;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Who is acting, resolved once per request. The tenant always comes from the authenticated identity
 * (a user's tenant or a credential's tenant), never from a client-supplied value.
 */
public record Actor(
        ActorType type,
        UUID id,
        TenantId tenantId,
        PermissionScope scope,
        String displayName,
        TenantSystemId tenantSystemId,
        Set<Permission> permissions) {

    public Actor {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(displayName, "displayName");
        permissions = Set.copyOf(permissions);
        if ((type == ActorType.TENANT_SYSTEM) != (tenantSystemId != null)) {
            throw new IllegalArgumentException("only a tenant-system actor carries a tenant system id");
        }
    }

    public Optional<TenantSystemId> tenantSystem() {
        return Optional.ofNullable(tenantSystemId);
    }

    /** The string recorded in audit entries, for example {@code user/<id>}. */
    public String auditName() {
        return (type == ActorType.USER ? "user/" : "tenant-system/") + id;
    }
}
