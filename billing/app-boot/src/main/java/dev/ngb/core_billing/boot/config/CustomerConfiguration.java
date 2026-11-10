package dev.ngb.core_billing.boot.config;

import dev.ngb.core_billing.customer.adapter.persistence.JdbcCustomerRepository;
import dev.ngb.core_billing.customer.adapter.persistence.customer.JdbcCustomerReadRepository;
import dev.ngb.core_billing.customer.api.lookup.CustomerLookup;
import dev.ngb.core_billing.customer.application.command.CreateCustomerUseCase;
import dev.ngb.core_billing.customer.application.command.CustomerAdministration;
import dev.ngb.core_billing.customer.application.query.CustomerLookupService;
import dev.ngb.core_billing.customer.application.query.CustomerQueries;
import dev.ngb.core_billing.customer.application.query.CustomerReadRepository;
import dev.ngb.core_billing.customer.application.port.CustomerRepository;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.api.idempotency.IdempotencyStore;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.sharedkernel.Clock;
import dev.ngb.core_billing.sharedkernel.EventPublisher;
import dev.ngb.core_billing.sharedkernel.IdGenerator;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Wires the customer module. Holds no business rules. */
@Configuration(proxyBeanMethods = false)
class CustomerConfiguration {

    @Bean
    CustomerRepository customerRepository(JdbcAggregateOperations jdbc, JdbcClient sql) {
        return new JdbcCustomerRepository(jdbc, sql);
    }

    @Bean
    CreateCustomerUseCase createCustomerUseCase(
            UnitOfWork unitOfWork,
            CustomerRepository customers,
            IdempotencyStore idempotency,
            EventPublisher events,
            AuditLog auditLog,
            TenantContext tenantContext,
            IdGenerator ids,
            Clock clock) {
        return new CreateCustomerUseCase(unitOfWork, customers, idempotency, events, auditLog, tenantContext, ids, clock);
    }

    @Bean
    CustomerAdministration customerAdministration(
            UnitOfWork unitOfWork, CustomerRepository customers, AuditLog auditLog, TenantContext tenantContext, Clock clock) {
        return new CustomerAdministration(unitOfWork, customers, auditLog, tenantContext, clock);
    }

    @Bean
    CustomerReadRepository customerReadRepository(JdbcClient jdbc) {
        return new JdbcCustomerReadRepository(jdbc);
    }

    @Bean
    CustomerQueries customerQueries(UnitOfWork unitOfWork, CustomerReadRepository customers) {
        return new CustomerQueries(unitOfWork, customers);
    }

    @Bean
    CustomerLookup customerLookup(UnitOfWork unitOfWork, CustomerReadRepository customers) {
        return new CustomerLookupService(unitOfWork, customers);
    }
}
