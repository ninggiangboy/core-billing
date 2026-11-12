package dev.ngb.tenant.identity.application.user.command;

import dev.ngb.tenant.identity.api.security.Role;
import dev.ngb.tenant.identity.api.account.AccountListener;
import dev.ngb.tenant.identity.api.account.RegisteredAccount;
import dev.ngb.tenant.identity.application.user.port.UserRepository;
import dev.ngb.tenant.identity.domain.user.User;
import dev.ngb.tenant.shared.error.NotFoundException;
import java.util.UUID;

/** Administrative operations on local accounts: listing, roles and deactivation. */
public class ManageUsersUseCase {

    private final UserRepository users;
    private final AccountListener listener;

    public ManageUsersUseCase(UserRepository users, AccountListener listener) {
        this.users = users;
        this.listener = listener;
    }

    public User get(UUID id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("user not found"));
    }

    public User changeRole(UUID id, Role role) {
        User user = get(id);
        User updated = user.withRole(role);
        users.save(updated);
        return updated;
    }

    public User deactivate(UUID id) {
        User user = get(id);
        User updated = user.disabled();
        users.save(updated);
        listener.deactivated(new RegisteredAccount(updated.id(), updated.displayName(), updated.email()));
        return updated;
    }
}
