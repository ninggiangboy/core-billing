package io.corebilling.platform.domain.user;

import io.corebilling.platform.api.actor.Permission;
import io.corebilling.platform.api.actor.PermissionScope;
import io.corebilling.sharedkernel.TenantId;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import io.corebilling.platform.domain.role.RoleId;

/**
 * An account that administers Billing, either at Platform Scope or inside one Tenant. It is linked to
 * its Keycloak identity by the OIDC subject; roles and permissions are owned here, not by Keycloak.
 */
public final class BillingUser {

    private final UserId id;
    private final TenantId tenantId;
    private final PermissionScope scope;
    private final String email;
    private final String displayName;
    private final String keycloakSubject;
    private final Instant createdAt;
    private UserStatus status;
    private final Set<RoleId> roleIds;
    private final Set<Permission> directPermissions;

    private BillingUser(
            UserId id,
            TenantId tenantId,
            PermissionScope scope,
            String email,
            String displayName,
            String keycloakSubject,
            UserStatus status,
            Instant createdAt,
            Set<RoleId> roleIds,
            Set<Permission> directPermissions) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.email = normalizeEmail(email);
        this.displayName = requireText(displayName, "displayName");
        this.keycloakSubject = requireText(keycloakSubject, "keycloakSubject");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.roleIds = new HashSet<>(roleIds);
        this.directPermissions = new HashSet<>(directPermissions);
        this.directPermissions.forEach(this::requireValidInScope);
    }

    public static BillingUser create(
            UserId id,
            TenantId tenantId,
            PermissionScope scope,
            String email,
            String displayName,
            String keycloakSubject,
            Instant now) {
        return new BillingUser(
                id, tenantId, scope, email, displayName, keycloakSubject, UserStatus.ACTIVE, now, Set.of(), Set.of());
    }

    public static BillingUser restore(
            UserId id,
            TenantId tenantId,
            PermissionScope scope,
            String email,
            String displayName,
            String keycloakSubject,
            UserStatus status,
            Instant createdAt,
            Set<RoleId> roleIds,
            Set<Permission> directPermissions) {
        return new BillingUser(
                id, tenantId, scope, email, displayName, keycloakSubject, status, createdAt, roleIds, directPermissions);
    }

    public void assign(RoleId roleId) {
        roleIds.add(Objects.requireNonNull(roleId, "roleId"));
    }

    public void unassign(RoleId roleId) {
        roleIds.remove(roleId);
    }

    public void grant(Permission permission) {
        requireValidInScope(permission);
        directPermissions.add(permission);
    }

    public void revoke(Permission permission) {
        directPermissions.remove(permission);
    }

    public void disable() {
        status = UserStatus.DISABLED;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public UserId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public PermissionScope scope() {
        return scope;
    }

    public String email() {
        return email;
    }

    public String displayName() {
        return displayName;
    }

    public String keycloakSubject() {
        return keycloakSubject;
    }

    public UserStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Set<RoleId> roleIds() {
        return Set.copyOf(roleIds);
    }

    public Set<Permission> directPermissions() {
        return Set.copyOf(directPermissions);
    }

    private void requireValidInScope(Permission permission) {
        if (!permission.validIn(scope)) {
            throw new IllegalArgumentException(permission.code() + " cannot be granted in " + scope + " scope");
        }
    }

    private static String normalizeEmail(String email) {
        String value = requireText(email, "email").toLowerCase(Locale.ROOT);
        int at = value.indexOf('@');
        if (at < 1 || at != value.lastIndexOf('@') || at == value.length() - 1 || value.length() > 254 || value.contains(" ")) {
            throw new IllegalArgumentException("invalid email address");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > 254) {
            throw new IllegalArgumentException(name + " must be 1-254 characters");
        }
        return value.strip();
    }
}
