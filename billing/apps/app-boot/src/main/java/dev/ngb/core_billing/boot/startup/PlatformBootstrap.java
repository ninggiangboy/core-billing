package dev.ngb.core_billing.boot.startup;

import dev.ngb.core_billing.boot.infrastructure.coordination.JobLeases;
import dev.ngb.core_billing.platform.api.actor.RequestScope;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.platform.application.user.command.BootstrapPlatformAdminUseCase;
import dev.ngb.core_billing.platform.application.tenant.command.EnsurePlatformDomainUseCase;
import dev.ngb.core_billing.platform.application.role.command.SeedDefaultRolesUseCase;
import dev.ngb.core_billing.platform.application.role.command.SeedPermissionsUseCase;
import dev.ngb.core_billing.platform.application.tenant.query.TenantQueries;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import dev.ngb.core_billing.platform.domain.tenant.Tenant;

/**
 * On every start: mirrors the permission catalog, maps platform.&lt;base-domain&gt; and creates the Platform's
 * default roles. A time-bounded lease makes one instance do it when several start together; the lease commits on its
 * own and is not held while Keycloak is called.
 */
@Profile("!migrate")
@Component
class PlatformBootstrap implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformBootstrap.class);
    private static final String LEASE = "platform-bootstrap";
    private static final Duration LEASE_TTL = Duration.ofMinutes(15);

    private final EnsurePlatformDomainUseCase ensurePlatformDomain;
    private final SeedPermissionsUseCase seedPermissions;
    private final SeedDefaultRolesUseCase seedDefaultRoles;
    private final BootstrapPlatformAdminUseCase bootstrapAdmin;
    private final TenantQueries tenantQueries;
    private final TenantContext tenantContext;
    private final JobLeases leases;
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
            JobLeases leases,
            @Value("${billing.tenancy.base-domain}") String baseDomain,
            @Value("${billing.bootstrap.platform-admin-email:}") String adminEmail,
            @Value("${billing.bootstrap.platform-admin-temporary-password:}") String adminTemporaryPassword) {
        this.ensurePlatformDomain = ensurePlatformDomain;
        this.seedPermissions = seedPermissions;
        this.seedDefaultRoles = seedDefaultRoles;
        this.bootstrapAdmin = bootstrapAdmin;
        this.tenantQueries = tenantQueries;
        this.tenantContext = tenantContext;
        this.leases = leases;
        this.baseDomain = baseDomain;
        this.adminEmail = adminEmail;
        this.adminTemporaryPassword = adminTemporaryPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!leases.tryAcquire(LEASE, LEASE_TTL)) {
            LOG.info("platform bootstrap is already running on another instance");
            return;
        }
        try {
            tenantContext.runAs(RequestScope.platform(), () -> {
                seedPermissions.execute();
                ensurePlatformDomain.execute("platform." + baseDomain);
                seedDefaultRoles.execute();
                // Tenants created before a permission existed get it on their default roles.
                tenantQueries.list().stream().filter(dev.ngb.core_billing.platform.application.tenant.query.TenantView::isActive).forEach(tenant ->
                        tenantContext.runAs(RequestScope.tenant(new dev.ngb.core_billing.sharedkernel.id.TenantId(tenant.id())), seedDefaultRoles::execute));
                if (!adminEmail.isBlank()) {
                    bootstrapAdmin.execute(adminEmail, "Platform Administrator", adminTemporaryPassword.isBlank() ? null : adminTemporaryPassword);
                }
            });
        } finally {
            leases.release(LEASE);
        }
    }
}
