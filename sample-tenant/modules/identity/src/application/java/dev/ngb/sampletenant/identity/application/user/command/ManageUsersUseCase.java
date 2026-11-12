package dev.ngb.sampletenant.identity.application.user.command;

import dev.ngb.sampletenant.identity.api.security.Role;
import dev.ngb.sampletenant.identity.api.account.AccountListener;
import dev.ngb.sampletenant.identity.api.account.RegisteredAccount;
import dev.ngb.sampletenant.identity.application.user.port.UserRepository;
import dev.ngb.sampletenant.identity.domain.user.User;
import dev.ngb.sampletenant.shared.error.NotFoundException;
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
