package dev.ngb.core_billing.platform.application.user.command;

import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.platform.application.messaging.service.PlatformActors;
import dev.ngb.core_billing.platform.application.role.command.SeedDefaultRolesUseCase;
import dev.ngb.core_billing.platform.application.role.port.RoleRepository;
import dev.ngb.core_billing.platform.application.user.port.UserRepository;
import dev.ngb.core_billing.platform.domain.role.Role;
import java.util.Locale;
import java.util.Set;

/**
 * Creates the first Platform administrator from deployment configuration, so a fresh Platform can be
 * administered at all. Does nothing when that email already exists.
 */
public class BootstrapPlatformAdminUseCase {

    private static final Audit BOOTSTRAP = new Audit(PlatformActors.BOOTSTRAP, "bootstrap");

    private final UnitOfWork unitOfWork;
    private final UserRepository users;
    private final RoleRepository roles;
    private final ManageUserUseCase manageUser;

    public BootstrapPlatformAdminUseCase(
            UnitOfWork unitOfWork, UserRepository users, RoleRepository roles, ManageUserUseCase manageUser) {
        this.unitOfWork = unitOfWork;
        this.users = users;
        this.roles = roles;
        this.manageUser = manageUser;
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
        manageUser.create(BOOTSTRAP, new ManageUserUseCase.CreateUser(normalized, displayName, temporaryPassword, Set.of(admin.id())));
        return true;
    }
}
