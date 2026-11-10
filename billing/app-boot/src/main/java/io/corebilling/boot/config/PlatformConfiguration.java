package io.corebilling.boot.config;

import io.corebilling.platform.adapter.persistence.JdbcTenantDomainRepository;
import io.corebilling.platform.adapter.persistence.JdbcTenantRepository;
import io.corebilling.platform.api.AuditLog;
import io.corebilling.platform.application.AddTenantDomainUseCase;
import io.corebilling.platform.application.CreateTenantUseCase;
import io.corebilling.platform.application.DeactivateTenantUseCase;
import io.corebilling.platform.application.EnsurePlatformDomainUseCase;
import io.corebilling.platform.application.ResolveHostUseCase;
import io.corebilling.platform.application.TenantDomainRepository;
import io.corebilling.platform.application.TenantRepository;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.IdGenerator;
import io.corebilling.sharedkernel.UnitOfWork;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;

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
}
