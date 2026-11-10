package io.corebilling.boot.config;

import io.corebilling.customer.adapter.persistence.JdbcCustomerRepository;
import io.corebilling.customer.api.lookup.CustomerLookup;
import io.corebilling.customer.application.command.CreateCustomerUseCase;
import io.corebilling.customer.application.command.CustomerAdministration;
import io.corebilling.customer.application.query.CustomerLookupService;
import io.corebilling.customer.application.port.CustomerRepository;
import io.corebilling.platform.api.AuditLog;
import io.corebilling.platform.api.IdempotencyStore;
import io.corebilling.platform.api.TenantContext;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.EventPublisher;
import io.corebilling.sharedkernel.IdGenerator;
import io.corebilling.sharedkernel.UnitOfWork;
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
    CustomerLookup customerLookup(UnitOfWork unitOfWork, CustomerRepository customers) {
        return new CustomerLookupService(unitOfWork, customers);
    }
}
