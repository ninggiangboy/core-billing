package dev.ngb.core_billing.platform.adapter.web;

import org.springframework.context.annotation.Profile;
import dev.ngb.core_billing.platform.api.actor.Permission;
import dev.ngb.core_billing.platform.api.actor.RequiresPermission;
import dev.ngb.core_billing.platform.application.user.command.UserAdministration;
import dev.ngb.core_billing.platform.application.user.query.UserQueries;
import dev.ngb.core_billing.platform.application.user.query.UserView;
import dev.ngb.core_billing.platform.domain.user.BillingUser;
import dev.ngb.core_billing.platform.domain.role.RoleId;
import dev.ngb.core_billing.platform.domain.user.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Billing Users of the current scope: Platform Users on the Platform, Tenant Users inside a Tenant. */
@Profile("!worker & !migrate")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    public record CreateUserRequest(String email, String displayName, String temporaryPassword, List<String> roleIds) {
        @Override
        public String toString() {
            return "CreateUserRequest[email=" + email + "]";
        }
    }

    public record UserResponse(
            String id, String email, String displayName, String status, Set<String> roleIds, Set<String> directPermissions, Instant createdAt) {
        static UserResponse of(BillingUser user) {
            return new UserResponse(
                    user.id().toString(),
                    user.email(),
                    user.displayName(),
                    user.status().name(),
                    user.roleIds().stream().map(RoleId::toString).collect(Collectors.toSet()),
                    user.directPermissions().stream().map(Permission::code).collect(Collectors.toSet()),
                    user.createdAt());
        }

        static UserResponse ofView(UserView user) {
            return new UserResponse(
                    user.id().toString(),
                    user.email(),
                    user.displayName(),
                    user.status().name(),
                    user.roleIds().stream().map(Object::toString).collect(Collectors.toSet()),
                    user.directPermissions().stream().map(Permission::code).collect(Collectors.toSet()),
                    user.createdAt());
        }
    }

    private final UserAdministration users;
    private final UserQueries userQueries;
    private final CallerResolver callers;

    public UserController(UserAdministration users, UserQueries userQueries, CallerResolver callers) {
        this.users = users;
        this.userQueries = userQueries;
        this.callers = callers;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.USER_MANAGE)
    public UserResponse create(@RequestBody CreateUserRequest request) {
        Set<RoleId> roleIds = request.roleIds() == null
                ? Set.of()
                : request.roleIds().stream().map(RoleId::of).collect(Collectors.toSet());
        return UserResponse.of(users.create(
                callers.current(), new UserAdministration.CreateUser(request.email(), request.displayName(), request.temporaryPassword(), roleIds)));
    }

    @GetMapping
    @RequiresPermission(Permission.USER_READ)
    public List<UserResponse> list() {
        return userQueries.list().stream().map(UserResponse::ofView).toList();
    }

    @GetMapping("/{id}")
    @RequiresPermission(Permission.USER_READ)
    public UserResponse get(@PathVariable String id) {
        return UserResponse.ofView(userQueries.get(UserId.of(id).value()));
    }

    @PutMapping("/{id}/roles/{roleId}")
    @RequiresPermission(Permission.USER_MANAGE)
    public UserResponse assignRole(@PathVariable String id, @PathVariable String roleId) {
        return UserResponse.of(users.assignRole(callers.current(), UserId.of(id), RoleId.of(roleId)));
    }

    @DeleteMapping("/{id}/roles/{roleId}")
    @RequiresPermission(Permission.USER_MANAGE)
    public UserResponse unassignRole(@PathVariable String id, @PathVariable String roleId) {
        return UserResponse.of(users.unassignRole(callers.current(), UserId.of(id), RoleId.of(roleId)));
    }
}
