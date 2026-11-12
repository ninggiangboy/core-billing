package dev.ngb.billing.platform.domain.role;

import dev.ngb.core.id.TenantId;
import dev.ngb.billing.platform.api.actor.Permission;
import dev.ngb.billing.platform.api.actor.PermissionScope;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** A customisable group of permissions that all belong to the scope the role lives in. */
public final class Role {

    private final RoleId id;
    private final TenantId tenantId;
    private final PermissionScope scope;
    private final String name;
    private final boolean system;
    private final Instant createdAt;
    private final Set<Permission> permissions;

    private Role(
            RoleId id,
            TenantId tenantId,
            PermissionScope scope,
            String name,
            boolean system,
            Instant createdAt,
            Set<Permission> permissions) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.scope = Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(name, "name");
        if (name.isBlank() || name.length() > 100) {
            throw new IllegalArgumentException("role name must be 1-100 characters");
        }
        this.name = name.strip();
        this.system = system;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.permissions = new HashSet<>();
        permissions.forEach(this::add);
    }

    public static Role create(
            RoleId id, TenantId tenantId, PermissionScope scope, String name, boolean system, Set<Permission> permissions, Instant now) {
        return new Role(id, tenantId, scope, name, system, now, permissions);
    }

    public static Role restore(
            RoleId id, TenantId tenantId, PermissionScope scope, String name, boolean system, Instant createdAt, Set<Permission> permissions) {
        return new Role(id, tenantId, scope, name, system, createdAt, permissions);
    }

    /** Replaces the permission set. A default role keeps the permissions it shipped with. */
    public void replacePermissions(Set<Permission> replacement) {
        if (system) {
            throw new IllegalStateException("a default role's permissions cannot be changed");
        }
        permissions.clear();
        replacement.forEach(this::add);
    }

    /** Brings a default role up to the permissions it now ships with, for example after the catalog grew. */
    public void syncDefaultPermissions(Set<Permission> defaults) {
        if (!system) {
            throw new IllegalStateException("only a default role is synchronised");
        }
        permissions.clear();
        defaults.forEach(this::add);
    }

    private void add(Permission permission) {
        if (!permission.validIn(scope)) {
            throw new IllegalArgumentException("%s is not valid in %s scope".formatted(permission.code(), scope));
        }
        permissions.add(permission);
    }

    public RoleId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public PermissionScope scope() {
        return scope;
    }

    public String name() {
        return name;
    }

    public boolean isSystem() {
        return system;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Set<Permission> permissions() {
        return Set.copyOf(permissions);
    }
}
