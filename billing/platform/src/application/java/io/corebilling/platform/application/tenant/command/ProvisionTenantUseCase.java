package io.corebilling.platform.application.tenant.command;

import io.corebilling.platform.api.actor.RequestScope;
import io.corebilling.platform.api.tenant.TenantContext;
import io.corebilling.platform.domain.role.Role;
import io.corebilling.platform.domain.tenant.Tenant;
import io.corebilling.sharedkernel.UnitOfWork;
import java.util.Set;
import io.corebilling.platform.application.auth.query.Caller;
import io.corebilling.platform.application.role.command.SeedDefaultRolesUseCase;
import io.corebilling.platform.application.role.port.RoleRepository;
import io.corebilling.platform.application.user.command.UserAdministration;

/**
 * Creates a Tenant, then initialises it from inside its own scope: default roles and, when requested,
 * the first administrator. The steps are idempotent, so a failure part-way is repaired by running the
 * remaining steps again rather than by undoing the Tenant.
 */
public class ProvisionTenantUseCase {

    public record Command(
            String code, String name, String adminEmail, String adminDisplayName, String adminTemporaryPassword,
            String actor, String requestId) {}

    private final CreateTenantUseCase createTenant;
    private final SeedDefaultRolesUseCase seedDefaultRoles;
    private final UserAdministration users;
    private final UnitOfWork unitOfWork;
    private final RoleRepository roles;
    private final TenantContext tenantContext;

    public ProvisionTenantUseCase(
            CreateTenantUseCase createTenant,
            SeedDefaultRolesUseCase seedDefaultRoles,
            UserAdministration users,
            UnitOfWork unitOfWork,
            RoleRepository roles,
            TenantContext tenantContext) {
        this.createTenant = createTenant;
        this.seedDefaultRoles = seedDefaultRoles;
        this.users = users;
        this.unitOfWork = unitOfWork;
        this.roles = roles;
        this.tenantContext = tenantContext;
    }

    public Tenant execute(Command command) {
        Tenant tenant = createTenant.execute(
                new CreateTenantUseCase.Command(command.code(), command.name(), command.actor(), command.requestId()));
        tenantContext.runAs(RequestScope.tenant(tenant.id()), () -> {
            seedDefaultRoles.execute();
            if (command.adminEmail() != null) {
                Role admin = unitOfWork.transactional(() -> roles.findByName(SeedDefaultRolesUseCase.TENANT_ADMIN)
                        .orElseThrow(() -> new IllegalStateException("default roles were not created")));
                users.create(
                        new Caller(command.actor(), command.requestId()),
                        new UserAdministration.CreateUser(
                                command.adminEmail(), command.adminDisplayName(), command.adminTemporaryPassword(),
                                Set.of(admin.id())));
            }
        });
        return tenant;
    }
}
