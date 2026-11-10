package io.corebilling.platform.application;

import io.corebilling.platform.domain.Role;
import io.corebilling.sharedkernel.UnitOfWork;
import java.util.Locale;
import java.util.Set;

/**
 * Creates the first Platform administrator from deployment configuration, so a fresh Platform can be
 * administered at all. Does nothing when that email already exists.
 */
public class BootstrapPlatformAdminUseCase {

    private static final Caller BOOTSTRAP = new Caller("system/bootstrap", "bootstrap");

    private final UnitOfWork unitOfWork;
    private final UserRepository users;
    private final RoleRepository roles;
    private final UserAdministration userAdministration;

    public BootstrapPlatformAdminUseCase(
            UnitOfWork unitOfWork, UserRepository users, RoleRepository roles, UserAdministration userAdministration) {
        this.unitOfWork = unitOfWork;
        this.users = users;
        this.roles = roles;
        this.userAdministration = userAdministration;
    }

    /** Runs at Platform scope. @return true when a user was created */
    public boolean execute(String email, String displayName, String temporaryPassword) {
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        Role admin = unitOfWork.transactional(() -> {
            if (users.findByEmail(normalized).isPresent()) {
                return null;
            }
            return roles.findByName(SeedDefaultRolesUseCase.PLATFORM_ADMIN)
                    .orElseThrow(() -> new IllegalStateException("default platform roles were not created"));
        });
        if (admin == null) {
            return false;
        }
        userAdministration.create(BOOTSTRAP, new UserAdministration.CreateUser(normalized, displayName, temporaryPassword, Set.of(admin.id())));
        return true;
    }
}
