package dev.ngb.core_billing.boot.config;

import dev.ngb.core_billing.platform.adapter.auth.keycloak.KeycloakIdentityProvider;
import dev.ngb.core_billing.platform.adapter.credential.persistence.JooqCredentialRepository;
import dev.ngb.core_billing.platform.adapter.role.persistence.JooqPermissionCatalogRepository;
import dev.ngb.core_billing.platform.adapter.role.persistence.JooqRoleRepository;
import dev.ngb.core_billing.platform.adapter.tenant.persistence.JooqTenantDomainRepository;
import dev.ngb.core_billing.platform.adapter.tenantsystem.persistence.JooqTenantSystemRepository;
import dev.ngb.core_billing.platform.adapter.user.persistence.JooqUserRepository;
import dev.ngb.core_billing.platform.adapter.tenant.persistence.JooqTenantRepository;
import dev.ngb.core_billing.platform.adapter.credential.persistence.JooqCredentialReadRepository;
import dev.ngb.core_billing.platform.adapter.role.persistence.JooqRoleReadRepository;
import dev.ngb.core_billing.platform.adapter.tenant.persistence.JooqTenantReadRepository;
import dev.ngb.core_billing.platform.adapter.tenantsystem.persistence.JooqTenantSystemReadRepository;
import dev.ngb.core_billing.platform.adapter.user.persistence.JooqUserReadRepository;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.platform.application.tenant.command.AddTenantDomainUseCase;
import dev.ngb.core_billing.platform.application.auth.service.Authentication;
import dev.ngb.core_billing.platform.application.user.command.BootstrapPlatformAdminUseCase;
import dev.ngb.core_billing.platform.application.credential.command.ManageCredentialUseCase;
import dev.ngb.core_billing.platform.application.credential.port.CredentialRepository;
import dev.ngb.core_billing.platform.application.credential.query.CredentialQueries;
import dev.ngb.core_billing.platform.application.credential.query.CredentialReadRepository;
import dev.ngb.core_billing.platform.application.auth.port.IdentityProviderPort;
import dev.ngb.core_billing.platform.application.role.port.PermissionCatalogRepository;
import dev.ngb.core_billing.platform.application.tenant.command.ProvisionTenantUseCase;
import dev.ngb.core_billing.platform.application.auth.port.RateLimiter;
import dev.ngb.core_billing.platform.application.role.command.ManageRoleUseCase;
import dev.ngb.core_billing.platform.application.role.port.RoleRepository;
import dev.ngb.core_billing.platform.application.role.query.RoleQueries;
import dev.ngb.core_billing.platform.application.role.query.RoleReadRepository;
import dev.ngb.core_billing.platform.application.role.command.SeedDefaultRolesUseCase;
import dev.ngb.core_billing.platform.application.role.command.SeedPermissionsUseCase;
import dev.ngb.core_billing.platform.application.tenant.query.TenantQueries;
import dev.ngb.core_billing.platform.application.tenant.query.TenantReadRepository;
import dev.ngb.core_billing.platform.application.tenantsystem.command.CreateTenantSystemUseCase;
import dev.ngb.core_billing.platform.application.tenantsystem.port.TenantSystemRepository;
import dev.ngb.core_billing.platform.application.tenantsystem.query.TenantSystemQueries;
import dev.ngb.core_billing.platform.application.tenantsystem.query.TenantSystemReadRepository;
import dev.ngb.core_billing.platform.application.user.command.ManageUserUseCase;
import dev.ngb.core_billing.platform.application.user.port.UserRepository;
import dev.ngb.core_billing.platform.application.user.query.UserQueries;
import dev.ngb.core_billing.platform.application.user.query.UserReadRepository;
import dev.ngb.core_billing.platform.application.tenant.command.CreateTenantUseCase;
import dev.ngb.core_billing.platform.application.tenant.command.DeactivateTenantUseCase;
import dev.ngb.core_billing.platform.application.tenant.command.EnsurePlatformDomainUseCase;
import dev.ngb.core_billing.platform.application.tenant.command.ResolveHostUseCase;
import dev.ngb.core_billing.platform.application.tenant.port.TenantDomainRepository;
import dev.ngb.core_billing.platform.application.tenant.port.TenantRepository;
import dev.ngb.core_billing.sharedkernel.Clock;
import dev.ngb.core_billing.sharedkernel.IdGenerator;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import java.security.SecureRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.client.RestClient;

/** Wires the platform module. Holds no business rules. */
@Configuration(proxyBeanMethods = false)
class PlatformConfiguration {

    @Bean
    TenantRepository tenantRepository(DSLContext dsl) {
        return new JooqTenantRepository(dsl);
    }

    @Bean
    TenantDomainRepository tenantDomainRepository(DSLContext dsl) {
        return new JooqTenantDomainRepository(dsl);
    }

    @Bean
    CreateTenantUseCase createTenantUseCase(
            UnitOfWork unitOfWork,
            TenantRepository tenants,
            TenantDomainRepository domains,
            AuditLog auditLog,
            IdGenerator ids,
            Clock clock,
            @Value("${billing.tenancy.base-domain}") String baseDomain) {
        return new CreateTenantUseCase(unitOfWork, tenants, domains, auditLog, ids, clock, baseDomain);
    }

    @Bean
    DeactivateTenantUseCase deactivateTenantUseCase(
            UnitOfWork unitOfWork, TenantRepository tenants, AuditLog auditLog, Clock clock) {
        return new DeactivateTenantUseCase(unitOfWork, tenants, auditLog, clock);
    }

    @Bean
    AddTenantDomainUseCase addTenantDomainUseCase(
            UnitOfWork unitOfWork,
            TenantRepository tenants,
            TenantDomainRepository domains,
            AuditLog auditLog,
            IdGenerator ids,
            Clock clock) {
        return new AddTenantDomainUseCase(unitOfWork, tenants, domains, auditLog, ids, clock);
    }

    @Bean
    EnsurePlatformDomainUseCase ensurePlatformDomainUseCase(
            UnitOfWork unitOfWork, TenantDomainRepository domains, IdGenerator ids, Clock clock) {
        return new EnsurePlatformDomainUseCase(unitOfWork, domains, ids, clock);
    }

    @Bean
    ResolveHostUseCase resolveHostUseCase(
            UnitOfWork unitOfWork, TenantDomainRepository domains, TenantRepository tenants) {
        return new ResolveHostUseCase(unitOfWork, domains, tenants);
    }

    @Bean
    UserRepository userRepository(DSLContext dsl) {
        return new JooqUserRepository(dsl);
    }

    @Bean
    RoleRepository roleRepository(DSLContext dsl) {
        return new JooqRoleRepository(dsl);
    }

    @Bean
    TenantSystemRepository tenantSystemRepository(DSLContext dsl) {
        return new JooqTenantSystemRepository(dsl);
    }

    @Bean
    CredentialRepository credentialRepository(DSLContext dsl) {
        return new JooqCredentialRepository(dsl);
    }

    @Bean
    PermissionCatalogRepository permissionCatalogRepository(DSLContext dsl) {
        return new JooqPermissionCatalogRepository(dsl);
    }

    /** Created on first use, so only code that really calls Keycloak needs its settings. */
    @Bean
    @Lazy
    IdentityProviderPort identityProvider(
            @Value("${billing.keycloak.base-url}") String baseUrl,
            @Value("${billing.keycloak.realm}") String realm,
            @Value("${billing.keycloak.admin-client-id}") String clientId,
            @Value("${billing.keycloak.admin-client-secret}") String clientSecret) {
        return new KeycloakIdentityProvider(RestClient.builder(), baseUrl, realm, clientId, clientSecret);
    }

    @Bean
    SeedPermissionsUseCase seedPermissionsUseCase(UnitOfWork unitOfWork, PermissionCatalogRepository catalog) {
        return new SeedPermissionsUseCase(unitOfWork, catalog);
    }

    @Bean
    SeedDefaultRolesUseCase seedDefaultRolesUseCase(
            UnitOfWork unitOfWork, RoleRepository roles, TenantContext tenantContext, IdGenerator ids, Clock clock) {
        return new SeedDefaultRolesUseCase(unitOfWork, roles, tenantContext, ids, clock);
    }

    @Bean
    ManageUserUseCase manageUser(
            UnitOfWork unitOfWork,
            UserRepository users,
            RoleRepository roles,
            TenantRepository tenants,
            @Lazy IdentityProviderPort identityProvider,
            AuditLog auditLog,
            TenantContext tenantContext,
            IdGenerator ids,
            Clock clock) {
        return new ManageUserUseCase(unitOfWork, users, roles, tenants, identityProvider, auditLog, tenantContext, ids, clock);
    }

    @Bean
    ManageRoleUseCase manageRole(
            UnitOfWork unitOfWork, RoleRepository roles, AuditLog auditLog, TenantContext tenantContext, IdGenerator ids, Clock clock) {
        return new ManageRoleUseCase(unitOfWork, roles, auditLog, tenantContext, ids, clock);
    }

    @Bean
    CreateTenantSystemUseCase createTenantSystem(
            UnitOfWork unitOfWork, TenantSystemRepository systems, AuditLog auditLog, TenantContext tenantContext, IdGenerator ids, Clock clock) {
        return new CreateTenantSystemUseCase(unitOfWork, systems, auditLog, tenantContext, ids, clock);
    }

    @Bean
    ManageCredentialUseCase manageCredential(
            UnitOfWork unitOfWork,
            CredentialRepository credentials,
            TenantSystemRepository systems,
            AuditLog auditLog,
            TenantContext tenantContext,
            IdGenerator ids,
            Clock clock) {
        return new ManageCredentialUseCase(unitOfWork, credentials, systems, auditLog, tenantContext, ids, clock, new SecureRandom());
    }

    @Bean
    Authentication authentication(
            UnitOfWork unitOfWork,
            UserRepository users,
            RoleRepository roles,
            TenantRepository tenants,
            CredentialRepository credentials,
            TenantSystemRepository systems,
            RateLimiter rateLimiter,
            TenantContext tenantContext,
            Clock clock) {
        return new Authentication(unitOfWork, users, roles, tenants, credentials, systems, rateLimiter, tenantContext, clock);
    }

    @Bean
    TenantReadRepository tenantReadRepository(DSLContext dsl) {
        return new JooqTenantReadRepository(dsl);
    }

    @Bean
    RoleReadRepository roleReadRepository(DSLContext dsl) {
        return new JooqRoleReadRepository(dsl);
    }

    @Bean
    UserReadRepository userReadRepository(DSLContext dsl) {
        return new JooqUserReadRepository(dsl);
    }

    @Bean
    TenantSystemReadRepository tenantSystemReadRepository(DSLContext dsl) {
        return new JooqTenantSystemReadRepository(dsl);
    }

    @Bean
    CredentialReadRepository credentialReadRepository(DSLContext dsl) {
        return new JooqCredentialReadRepository(dsl);
    }

    @Bean
    TenantQueries tenantQueries(UnitOfWork unitOfWork, TenantReadRepository tenants) {
        return new TenantQueries(unitOfWork, tenants);
    }

    @Bean
    RoleQueries roleQueries(UnitOfWork unitOfWork, RoleReadRepository roles) {
        return new RoleQueries(unitOfWork, roles);
    }

    @Bean
    UserQueries userQueries(UnitOfWork unitOfWork, UserReadRepository users) {
        return new UserQueries(unitOfWork, users);
    }

    @Bean
    TenantSystemQueries tenantSystemQueries(UnitOfWork unitOfWork, TenantSystemReadRepository systems) {
        return new TenantSystemQueries(unitOfWork, systems);
    }

    @Bean
    CredentialQueries credentialQueries(
            UnitOfWork unitOfWork, CredentialReadRepository credentials, TenantSystemReadRepository systems) {
        return new CredentialQueries(unitOfWork, credentials, systems);
    }

    @Bean
    ProvisionTenantUseCase provisionTenantUseCase(
            CreateTenantUseCase createTenant,
            SeedDefaultRolesUseCase seedDefaultRoles,
            ManageUserUseCase users,
            UnitOfWork unitOfWork,
            RoleRepository roles,
            TenantContext tenantContext) {
        return new ProvisionTenantUseCase(createTenant, seedDefaultRoles, users, unitOfWork, roles, tenantContext);
    }

    @Bean
    BootstrapPlatformAdminUseCase bootstrapPlatformAdminUseCase(
            UnitOfWork unitOfWork, UserRepository users, RoleRepository roles, ManageUserUseCase manageUser) {
        return new BootstrapPlatformAdminUseCase(unitOfWork, users, roles, manageUser);
    }
}
