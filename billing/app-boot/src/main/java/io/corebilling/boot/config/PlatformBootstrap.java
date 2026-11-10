package io.corebilling.boot.config;

import io.corebilling.platform.api.RequestScope;
import io.corebilling.platform.api.TenantContext;
import io.corebilling.platform.application.EnsurePlatformDomainUseCase;
import io.corebilling.platform.application.SeedDefaultRolesUseCase;
import io.corebilling.platform.application.SeedPermissionsUseCase;
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
    private final TenantContext tenantContext;
    private final String baseDomain;

    PlatformBootstrap(
            EnsurePlatformDomainUseCase ensurePlatformDomain,
            SeedPermissionsUseCase seedPermissions,
            SeedDefaultRolesUseCase seedDefaultRoles,
            TenantContext tenantContext,
            @Value("${billing.tenancy.base-domain}") String baseDomain) {
        this.ensurePlatformDomain = ensurePlatformDomain;
        this.seedPermissions = seedPermissions;
        this.seedDefaultRoles = seedDefaultRoles;
        this.tenantContext = tenantContext;
        this.baseDomain = baseDomain;
    }

    @Override
    public void run(ApplicationArguments args) {
        tenantContext.runAs(RequestScope.platform(), () -> {
            seedPermissions.execute();
            ensurePlatformDomain.execute("platform." + baseDomain);
            seedDefaultRoles.execute();
        });
    }
}
