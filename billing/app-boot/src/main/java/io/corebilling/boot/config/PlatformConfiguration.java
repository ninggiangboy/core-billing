package io.corebilling.boot.config;

import io.corebilling.platform.adapter.keycloak.KeycloakIdentityProvider;
import io.corebilling.platform.adapter.persistence.JdbcCredentialRepository;
import io.corebilling.platform.adapter.persistence.JdbcPermissionCatalogRepository;
import io.corebilling.platform.adapter.persistence.JdbcRoleRepository;
import io.corebilling.platform.adapter.persistence.JdbcTenantDomainRepository;
import io.corebilling.platform.adapter.persistence.JdbcTenantSystemRepository;
import io.corebilling.platform.adapter.persistence.JdbcUserRepository;
import io.corebilling.platform.adapter.security.InMemoryRateLimiter;
import io.corebilling.platform.adapter.persistence.JdbcTenantRepository;
import io.corebilling.platform.api.AuditLog;
import io.corebilling.platform.api.TenantContext;
import io.corebilling.platform.application.AddTenantDomainUseCase;
import io.corebilling.platform.application.Authentication;
import io.corebilling.platform.application.CredentialAdministration;
import io.corebilling.platform.application.CredentialRepository;
import io.corebilling.platform.application.IdentityProviderPort;
import io.corebilling.platform.application.PermissionCatalogRepository;
import io.corebilling.platform.application.ProvisionTenantUseCase;
import io.corebilling.platform.application.RateLimiter;
import io.corebilling.platform.application.RoleAdministration;
import io.corebilling.platform.application.RoleRepository;
import io.corebilling.platform.application.SeedDefaultRolesUseCase;
import io.corebilling.platform.application.SeedPermissionsUseCase;
import io.corebilling.platform.application.TenantQueries;
import io.corebilling.platform.application.TenantSystemAdministration;
import io.corebilling.platform.application.TenantSystemRepository;
import io.corebilling.platform.application.UserAdministration;
import io.corebilling.platform.application.UserRepository;
import io.corebilling.platform.application.CreateTenantUseCase;
import io.corebilling.platform.application.DeactivateTenantUseCase;
import io.corebilling.platform.application.EnsurePlatformDomainUseCase;
import io.corebilling.platform.application.ResolveHostUseCase;
import io.corebilling.platform.application.TenantDomainRepository;
import io.corebilling.platform.application.TenantRepository;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.IdGenerator;
import io.corebilling.sharedkernel.UnitOfWork;
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
            RestClient.Builder http,
            @Value("${billing.keycloak.base-url}") String baseUrl,
            @Value("${billing.keycloak.realm}") String realm,
            @Value("${billing.keycloak.admin-client-id}") String clientId,
            @Value("${billing.keycloak.admin-client-secret}") String clientSecret) {
        return new KeycloakIdentityProvider(http, baseUrl, realm, clientId, clientSecret);
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
    TenantQueries tenantQueries(UnitOfWork unitOfWork, TenantRepository tenants, TenantDomainRepository domains) {
        return new TenantQueries(unitOfWork, tenants, domains);
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
}
