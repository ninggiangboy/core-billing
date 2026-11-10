package io.corebilling.boot.config;

import io.corebilling.platform.api.RequestScope;
import io.corebilling.platform.api.TenantContext;
import io.corebilling.platform.application.BootstrapPlatformAdminUseCase;
import io.corebilling.platform.application.EnsurePlatformDomainUseCase;
import io.corebilling.platform.application.SeedDefaultRolesUseCase;
import io.corebilling.platform.application.SeedPermissionsUseCase;
import io.corebilling.platform.application.TenantQueries;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** On every start: mirrors the permission catalog, maps platform.&lt;base-domain&gt; and creates the Platform's default roles. */
@Component
class PlatformBootstrap implements ApplicationRunner {

    private final EnsurePlatformDomainUseCase ensurePlatformDomain;
    private final SeedPermissionsUseCase seedPermissions;
    private final SeedDefaultRolesUseCase seedDefaultRoles;
    private final BootstrapPlatformAdminUseCase bootstrapAdmin;
    private final TenantQueries tenantQueries;
    private final TenantContext tenantContext;
    private final String baseDomain;
    private final String adminEmail;
    private final String adminTemporaryPassword;

    PlatformBootstrap(
            EnsurePlatformDomainUseCase ensurePlatformDomain,
            SeedPermissionsUseCase seedPermissions,
            SeedDefaultRolesUseCase seedDefaultRoles,
            BootstrapPlatformAdminUseCase bootstrapAdmin,
            TenantQueries tenantQueries,
            TenantContext tenantContext,
            @Value("${billing.tenancy.base-domain}") String baseDomain,
            @Value("${billing.bootstrap.platform-admin-email:}") String adminEmail,
            @Value("${billing.bootstrap.platform-admin-temporary-password:}") String adminTemporaryPassword) {
        this.ensurePlatformDomain = ensurePlatformDomain;
        this.seedPermissions = seedPermissions;
        this.seedDefaultRoles = seedDefaultRoles;
        this.bootstrapAdmin = bootstrapAdmin;
        this.tenantQueries = tenantQueries;
        this.tenantContext = tenantContext;
        this.baseDomain = baseDomain;
        this.adminEmail = adminEmail;
        this.adminTemporaryPassword = adminTemporaryPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        tenantContext.runAs(RequestScope.platform(), () -> {
            seedPermissions.execute();
            ensurePlatformDomain.execute("platform." + baseDomain);
            seedDefaultRoles.execute();
            // Tenants created before a permission existed get it on their default roles.
            tenantQueries.list().stream().filter(io.corebilling.platform.domain.Tenant::isActive).forEach(tenant ->
                    tenantContext.runAs(RequestScope.tenant(tenant.id()), seedDefaultRoles::execute));
            if (!adminEmail.isBlank()) {
                bootstrapAdmin.execute(adminEmail, "Platform Administrator", adminTemporaryPassword.isBlank() ? null : adminTemporaryPassword);
            }
        });
    }
}
