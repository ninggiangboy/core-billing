package dev.ngb.core_billing.platform.application.role.command;

import dev.ngb.core_billing.platform.api.actor.Permission;
import dev.ngb.core_billing.platform.api.actor.PermissionScope;
import dev.ngb.core_billing.platform.api.actor.RequestScope;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.platform.domain.role.Role;
import dev.ngb.core_billing.platform.domain.role.RoleId;
import dev.ngb.core_billing.sharedkernel.runtime.Clock;
import dev.ngb.core_billing.sharedkernel.runtime.IdGenerator;
import dev.ngb.core_billing.sharedkernel.runtime.UnitOfWork;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import dev.ngb.core_billing.platform.application.role.port.RoleRepository;

/** Creates the default roles of the current scope when missing and keeps their permissions current. */
public class SeedDefaultRolesUseCase {

    public static final String PLATFORM_ADMIN = "Platform Admin";
    public static final String TENANT_ADMIN = "Tenant Admin";
    public static final String TENANT_VIEWER = "Tenant Viewer";

    private final UnitOfWork unitOfWork;
    private final RoleRepository roles;
    private final TenantContext tenantContext;
    private final IdGenerator ids;
    private final Clock clock;

    public SeedDefaultRolesUseCase(
            UnitOfWork unitOfWork, RoleRepository roles, TenantContext tenantContext, IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.roles = roles;
        this.tenantContext = tenantContext;
        this.ids = ids;
        this.clock = clock;
    }

    public void execute() {
        unitOfWork.transactional(() -> {
            RequestScope current = tenantContext.current();
            PermissionScope scope = current instanceof RequestScope.Platform ? PermissionScope.PLATFORM : PermissionScope.TENANT;
            defaults(scope).forEach((name, permissions) -> {
                var existing = roles.findByName(name);
                if (existing.isEmpty()) {
                    roles.add(Role.create(new RoleId(ids.newId()), current.dataTenantId(), scope, name, true, permissions, clock.now()));
                } else if (existing.get().isSystem() && !existing.get().permissions().equals(permissions)) {
                    // The catalog grew since this tenant was created: its default role gets the new rights.
                    Role role = existing.get();
                    role.syncDefaultPermissions(permissions);
                    roles.update(role);
                }
            });
        });
    }

    private static Map<String, Set<Permission>> defaults(PermissionScope scope) {
        Set<Permission> all = Arrays.stream(Permission.values()).filter(p -> p.validIn(scope)).collect(Collectors.toSet());
        Map<String, Set<Permission>> result = new LinkedHashMap<>();
        if (scope == PermissionScope.PLATFORM) {
            result.put(PLATFORM_ADMIN, all);
        } else {
            result.put(TENANT_ADMIN, all);
            result.put(TENANT_VIEWER, all.stream().filter(p -> p.code().endsWith(":read")).collect(Collectors.toSet()));
        }
        return result;
    }
}
