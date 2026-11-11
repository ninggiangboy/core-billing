package dev.ngb.sampletenant.identity.adapter.web;

import dev.ngb.sampletenant.identity.api.AuthenticatedUser;
import dev.ngb.sampletenant.identity.api.Permission;
import dev.ngb.sampletenant.identity.api.RequiresPermission;
import dev.ngb.sampletenant.identity.api.Role;
import dev.ngb.sampletenant.identity.application.ManageUsersUseCase;
import dev.ngb.sampletenant.identity.application.RegisterUserUseCase;
import dev.ngb.sampletenant.identity.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Account administration for business roles and permissions. */
@RestController
@RequestMapping("/api/users")
public class UserController {

    public record UserResponse(UUID id, String email, String displayName, Role role, String status, Instant createdAt) {
        static UserResponse of(User user) {
            return new UserResponse(user.id(), user.email(), user.displayName(), user.role(), user.status().name(), user.createdAt());
        }
    }

    public record CreateUserRequest(String email, String displayName, String password, Role role) {}

    public record ChangeRoleRequest(Role role) {}

    private final ManageUsersUseCase manage;
    private final RegisterUserUseCase register;

    public UserController(ManageUsersUseCase manage, RegisterUserUseCase register) {
        this.manage = manage;
        this.register = register;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return UserResponse.of(manage.get(principal.id()));
    }

    @GetMapping
    @RequiresPermission(Permission.USER_MANAGE)
    public List<UserResponse> list() {
        return manage.list().stream().map(UserResponse::of).toList();
    }

    @GetMapping("/{id}")
    @RequiresPermission(Permission.USER_MANAGE)
    public UserResponse get(@PathVariable UUID id) {
        return UserResponse.of(manage.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.USER_MANAGE)
    public UserResponse create(@RequestBody CreateUserRequest request) {
        User user = register.execute(new RegisterUserUseCase.Command(
                request.email(), request.displayName(), request.password(), request.role()));
        return UserResponse.of(user);
    }

    @PutMapping("/{id}/role")
    @RequiresPermission(Permission.USER_MANAGE)
    public UserResponse changeRole(@PathVariable UUID id, @RequestBody ChangeRoleRequest request) {
        return UserResponse.of(manage.changeRole(id, request.role()));
    }

    @PostMapping("/{id}/deactivate")
    @RequiresPermission(Permission.USER_MANAGE)
    public UserResponse deactivate(@PathVariable UUID id) {
        return UserResponse.of(manage.deactivate(id));
    }
}
