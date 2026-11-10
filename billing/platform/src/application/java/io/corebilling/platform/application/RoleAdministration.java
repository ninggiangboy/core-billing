package io.corebilling.platform.application;

import io.corebilling.platform.api.AuditEntry;
import io.corebilling.platform.api.AuditLog;
import io.corebilling.platform.api.Permission;
import io.corebilling.platform.api.PermissionScope;
import io.corebilling.platform.api.RequestScope;
import io.corebilling.platform.api.TenantContext;
import io.corebilling.platform.domain.Role;
import io.corebilling.platform.domain.RoleId;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.IdGenerator;
import io.corebilling.sharedkernel.UnitOfWork;
import java.util.List;
import java.util.Set;

/** Roles of the current scope. Roles live in the database and can be changed through the UI. */
public class RoleAdministration {

    private final UnitOfWork unitOfWork;
    private final RoleRepository roles;
    private final AuditLog auditLog;
    private final TenantContext tenantContext;
    private final IdGenerator ids;
    private final Clock clock;

    public RoleAdministration(
            UnitOfWork unitOfWork, RoleRepository roles, AuditLog auditLog, TenantContext tenantContext, IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.roles = roles;
        this.auditLog = auditLog;
        this.tenantContext = tenantContext;
        this.ids = ids;
        this.clock = clock;
    }

    public Role create(Caller caller, String name, Set<Permission> permissions) {
        return unitOfWork.transactional(() -> {
            RequestScope current = tenantContext.current();
            PermissionScope scope = current instanceof RequestScope.Platform ? PermissionScope.PLATFORM : PermissionScope.TENANT;
            if (roles.findByName(name == null ? "" : name.strip()).isPresent()) {
                throw new ConflictException("a role with this name already exists");
            }
            Role role = Role.create(new RoleId(ids.newId()), current.dataTenantId(), scope, name, false, permissions, clock.now());
            roles.add(role);
            auditLog.record(AuditEntry.tenant(role.tenantId(), caller.actor(), "role.created", "role/" + role.id(), caller.requestId(), clock.now()));
            return role;
        });
    }

    public Role replacePermissions(Caller caller, RoleId id, Set<Permission> permissions) {
        return unitOfWork.transactional(() -> {
            Role role = roles.findById(id).orElseThrow(() -> new NotFoundException("role not found"));
            try {
                role.replacePermissions(permissions);
            } catch (IllegalStateException defaultRole) {
                throw new ConflictException(defaultRole.getMessage());
            }
            roles.update(role);
            auditLog.record(AuditEntry.tenant(role.tenantId(), caller.actor(), "role.permissions-changed", "role/" + role.id(), caller.requestId(), clock.now()));
            return role;
        });
    }

    public Role get(RoleId id) {
        return unitOfWork.transactional(() -> roles.findById(id).orElseThrow(() -> new NotFoundException("role not found")));
    }

    public List<Role> list() {
        return unitOfWork.transactional(roles::list);
    }
}
