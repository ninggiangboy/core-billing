package dev.ngb.core_billing.boot.config;

import dev.ngb.core_billing.platform.adapter.keycloak.KeycloakIdentityProvider;
import dev.ngb.core_billing.platform.adapter.persistence.credential.JdbcCredentialRepository;
import dev.ngb.core_billing.platform.adapter.persistence.role.JdbcPermissionCatalogRepository;
import dev.ngb.core_billing.platform.adapter.persistence.role.JdbcRoleRepository;
import dev.ngb.core_billing.platform.adapter.persistence.tenant.JdbcTenantDomainRepository;
import dev.ngb.core_billing.platform.adapter.persistence.tenantsystem.JdbcTenantSystemRepository;
import dev.ngb.core_billing.platform.adapter.persistence.user.JdbcUserRepository;
import dev.ngb.core_billing.platform.adapter.security.InMemoryRateLimiter;
import dev.ngb.core_billing.platform.adapter.persistence.tenant.JdbcTenantRepository;
import dev.ngb.core_billing.platform.adapter.persistence.credential.JdbcCredentialReadRepository;
import dev.ngb.core_billing.platform.adapter.persistence.role.JdbcRoleReadRepository;
import dev.ngb.core_billing.platform.adapter.persistence.tenant.JdbcTenantReadRepository;
import dev.ngb.core_billing.platform.adapter.persistence.tenantsystem.JdbcTenantSystemReadRepository;
import dev.ngb.core_billing.platform.adapter.persistence.user.JdbcUserReadRepository;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.platform.application.tenant.command.AddTenantDomainUseCase;
import dev.ngb.core_billing.platform.application.auth.Authentication;
import dev.ngb.core_billing.platform.application.user.command.BootstrapPlatformAdminUseCase;
import dev.ngb.core_billing.platform.application.credential.command.CredentialAdministration;
import dev.ngb.core_billing.platform.application.credential.port.CredentialRepository;
import dev.ngb.core_billing.platform.application.credential.query.CredentialQueries;
import dev.ngb.core_billing.platform.application.credential.query.CredentialReadRepository;
import dev.ngb.core_billing.platform.application.auth.port.IdentityProviderPort;
import dev.ngb.core_billing.platform.application.role.port.PermissionCatalogRepository;
import dev.ngb.core_billing.platform.application.tenant.command.ProvisionTenantUseCase;
import dev.ngb.core_billing.platform.application.auth.port.RateLimiter;
import dev.ngb.core_billing.platform.application.role.command.RoleAdministration;
import dev.ngb.core_billing.platform.application.role.port.RoleRepository;
import dev.ngb.core_billing.platform.application.role.query.RoleQueries;
import dev.ngb.core_billing.platform.application.role.query.RoleReadRepository;
import dev.ngb.core_billing.platform.application.role.command.SeedDefaultRolesUseCase;
import dev.ngb.core_billing.platform.application.role.command.SeedPermissionsUseCase;
import dev.ngb.core_billing.platform.application.tenant.query.TenantQueries;
import dev.ngb.core_billing.platform.application.tenant.query.TenantReadRepository;
import dev.ngb.core_billing.platform.application.tenantsystem.command.TenantSystemAdministration;
import dev.ngb.core_billing.platform.application.tenantsystem.port.TenantSystemRepository;
import dev.ngb.core_billing.platform.application.tenantsystem.query.TenantSystemQueries;
import dev.ngb.core_billing.platform.application.tenantsystem.query.TenantSystemReadRepository;
import dev.ngb.core_billing.platform.application.user.command.UserAdministration;
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
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

/** Wires the platform module. Holds no business rules. */
@Configuration(proxyBeanMethods = false)
class PlatformConfiguration {

    @Bean
    TenantRepository tenantRepository(JdbcAggregateOperations jdbc) {
        return new JdbcTenantRepository(jdbc);
    }

    @Bean
    TenantDomainRepository tenantDomainRepository(JdbcAggregateOperations jdbc) {
        return new JdbcTenantDomainRepository(jdbc);
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
    UserRepository userRepository(JdbcAggregateOperations jdbc) {
        return new JdbcUserRepository(jdbc);
    }

    @Bean
    RoleRepository roleRepository(JdbcAggregateOperations jdbc) {
        return new JdbcRoleRepository(jdbc);
    }

    @Bean
    TenantSystemRepository tenantSystemRepository(JdbcAggregateOperations jdbc) {
        return new JdbcTenantSystemRepository(jdbc);
    }

    @Bean
    CredentialRepository credentialRepository(JdbcAggregateOperations jdbc) {
        return new JdbcCredentialRepository(jdbc);
    }

    @Bean
    PermissionCatalogRepository permissionCatalogRepository(JdbcClient jdbc) {
        return new JdbcPermissionCatalogRepository(jdbc);
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
    RateLimiter apiKeyRateLimiter(
            @Value("${billing.security.api-key-rate-limit.burst:100}") int burst,
            @Value("${billing.security.api-key-rate-limit.per-second:20}") double perSecond) {
        return new InMemoryRateLimiter(burst, perSecond, 10_000, System::nanoTime);
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
    UserAdministration userAdministration(
            UnitOfWork unitOfWork,
            UserRepository users,
            RoleRepository roles,
            TenantRepository tenants,
            @Lazy IdentityProviderPort identityProvider,
            AuditLog auditLog,
            TenantContext tenantContext,
            IdGenerator ids,
            Clock clock) {
        return new UserAdministration(unitOfWork, users, roles, tenants, identityProvider, auditLog, tenantContext, ids, clock);
    }

    @Bean
    RoleAdministration roleAdministration(
            UnitOfWork unitOfWork, RoleRepository roles, AuditLog auditLog, TenantContext tenantContext, IdGenerator ids, Clock clock) {
        return new RoleAdministration(unitOfWork, roles, auditLog, tenantContext, ids, clock);
    }

    @Bean
    TenantSystemAdministration tenantSystemAdministration(
            UnitOfWork unitOfWork, TenantSystemRepository systems, AuditLog auditLog, TenantContext tenantContext, IdGenerator ids, Clock clock) {
        return new TenantSystemAdministration(unitOfWork, systems, auditLog, tenantContext, ids, clock);
    }

    @Bean
    CredentialAdministration credentialAdministration(
            UnitOfWork unitOfWork,
            CredentialRepository credentials,
            TenantSystemRepository systems,
            AuditLog auditLog,
            TenantContext tenantContext,
            IdGenerator ids,
            Clock clock) {
        return new CredentialAdministration(unitOfWork, credentials, systems, auditLog, tenantContext, ids, clock, new SecureRandom());
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
    TenantReadRepository tenantReadRepository(JdbcClient jdbc) {
        return new JdbcTenantReadRepository(jdbc);
    }

    @Bean
    RoleReadRepository roleReadRepository(JdbcClient jdbc) {
        return new JdbcRoleReadRepository(jdbc);
    }

    @Bean
    UserReadRepository userReadRepository(JdbcClient jdbc) {
        return new JdbcUserReadRepository(jdbc);
    }

    @Bean
    TenantSystemReadRepository tenantSystemReadRepository(JdbcClient jdbc) {
        return new JdbcTenantSystemReadRepository(jdbc);
    }

    @Bean
    CredentialReadRepository credentialReadRepository(JdbcClient jdbc) {
        return new JdbcCredentialReadRepository(jdbc);
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
            UserAdministration users,
            UnitOfWork unitOfWork,
            RoleRepository roles,
            TenantContext tenantContext) {
        return new ProvisionTenantUseCase(createTenant, seedDefaultRoles, users, unitOfWork, roles, tenantContext);
    }

    @Bean
    BootstrapPlatformAdminUseCase bootstrapPlatformAdminUseCase(
            UnitOfWork unitOfWork, UserRepository users, RoleRepository roles, UserAdministration userAdministration) {
        return new BootstrapPlatformAdminUseCase(unitOfWork, users, roles, userAdministration);
    }
}
