package dev.ngb.billing.platform.adapter.role.web;

import org.springframework.context.annotation.Profile;
import dev.ngb.billing.platform.api.actor.Permission;
import dev.ngb.billing.platform.api.actor.PermissionScope;
import dev.ngb.billing.platform.api.actor.RequestScope;
import dev.ngb.billing.platform.api.actor.RequiresPermission;
import dev.ngb.billing.platform.api.tenant.TenantContext;
import dev.ngb.billing.platform.application.role.command.ManageRoleUseCase;
import dev.ngb.billing.platform.application.role.query.RoleQueries;
import dev.ngb.billing.platform.application.role.query.RoleView;
import dev.ngb.billing.platform.domain.role.Role;
import dev.ngb.billing.platform.domain.role.RoleId;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import dev.ngb.billing.platform.adapter.auth.web.CallerResolver;

/** Roles of the current scope, and the permission catalog they are built from. */
@Profile("api")
@RestController
@RequestMapping("/api/v1")
public class RoleController {

    public record CreateRoleRequest(String name, List<String> permissions) {}

    public record SetPermissionsRequest(List<String> permissions) {}

    public record RoleResponse(String id, String name, boolean system, Set<String> permissions, Instant createdAt) {
        static RoleResponse of(Role role) {
            return new RoleResponse(
                    role.id().toString(), role.name(), role.isSystem(),
                    role.permissions().stream().map(Permission::code).collect(Collectors.toSet()), role.createdAt());
        }

        static RoleResponse ofView(RoleView role) {
            return new RoleResponse(
                    role.id().toString(), role.name(), role.system(),
                    role.permissions().stream().map(Permission::code).collect(Collectors.toSet()), role.createdAt());
        }
    }

    private final ManageRoleUseCase roles;
    private final RoleQueries roleQueries;
    private final CallerResolver callers;
    private final TenantContext tenantContext;

    public RoleController(ManageRoleUseCase roles, RoleQueries roleQueries, CallerResolver callers, TenantContext tenantContext) {
        this.roles = roles;
        this.roleQueries = roleQueries;
        this.callers = callers;
        this.tenantContext = tenantContext;
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.ROLE_MANAGE)
    public RoleResponse create(@RequestBody CreateRoleRequest request) {
        return RoleResponse.of(roles.create(callers.current(), request.name(), parse(request.permissions())));
    }

    @GetMapping("/roles")
    @RequiresPermission(Permission.ROLE_READ)
    public List<RoleResponse> list() {
        return roleQueries.list().stream().map(RoleResponse::ofView).toList();
    }

    @GetMapping("/roles/{id}")
    @RequiresPermission(Permission.ROLE_READ)
    public RoleResponse get(@PathVariable String id) {
        return RoleResponse.ofView(roleQueries.get(RoleId.of(id).value()));
    }

    @PutMapping("/roles/{id}/permissions")
    @RequiresPermission(Permission.ROLE_MANAGE)
    public RoleResponse setPermissions(@PathVariable String id, @RequestBody SetPermissionsRequest request) {
        return RoleResponse.of(roles.replacePermissions(callers.current(), RoleId.of(id), parse(request.permissions())));
    }

    /** The permissions that can be put in a role in the current scope. */
    @GetMapping("/permissions")
    @RequiresPermission(Permission.ROLE_READ)
    public List<String> catalog() {
        PermissionScope scope = tenantContext.current() instanceof RequestScope.Platform ? PermissionScope.PLATFORM : PermissionScope.TENANT;
        return Arrays.stream(Permission.values()).filter(p -> p.validIn(scope)).map(Permission::code).sorted().toList();
    }

    private static Set<Permission> parse(List<String> codes) {
        if (codes == null) {
            return Set.of();
        }
        return codes.stream()
                .map(code -> Permission.fromCode(code).orElseThrow(() -> new IllegalArgumentException("unknown permission: " + code)))
                .collect(Collectors.toSet());
    }
}
