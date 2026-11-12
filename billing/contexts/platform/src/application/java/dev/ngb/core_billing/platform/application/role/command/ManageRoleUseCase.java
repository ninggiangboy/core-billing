package dev.ngb.core_billing.platform.application.role.command;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.core_billing.platform.api.actor.Permission;
import dev.ngb.core_billing.platform.api.actor.PermissionScope;
import dev.ngb.core_billing.platform.api.actor.RequestScope;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.platform.api.audit.AuditEntry;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.platform.application.role.port.RoleRepository;
import dev.ngb.core_billing.platform.domain.role.Role;
import dev.ngb.core_billing.platform.domain.role.RoleId;
import java.util.Set;

/** Roles of the current scope. Roles live in the database and can be changed through the UI. */
public class ManageRoleUseCase {

    private final UnitOfWork unitOfWork;
    private final RoleRepository roles;
    private final AuditLog auditLog;
    private final TenantContext tenantContext;
    private final IdGenerator ids;
    private final Clock clock;

    public ManageRoleUseCase(
            UnitOfWork unitOfWork, RoleRepository roles, AuditLog auditLog, TenantContext tenantContext, IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.roles = roles;
        this.auditLog = auditLog;
        this.tenantContext = tenantContext;
        this.ids = ids;
        this.clock = clock;
    }

    public Role create(Audit caller, String name, Set<Permission> permissions) {
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

    public Role replacePermissions(Audit caller, RoleId id, Set<Permission> permissions) {
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

}
