package dev.ngb.core_billing.boot.config;

import dev.ngb.core_billing.customer.adapter.customer.persistence.JooqCustomerRepository;
import dev.ngb.core_billing.customer.adapter.customer.persistence.JooqCustomerReadRepository;
import dev.ngb.core_billing.customer.api.lookup.CustomerLookup;
import dev.ngb.core_billing.customer.application.customer.command.CreateCustomerUseCase;
import dev.ngb.core_billing.customer.application.customer.command.ManageCustomerUseCase;
import dev.ngb.core_billing.customer.application.customer.service.CustomerLookupService;
import dev.ngb.core_billing.customer.application.customer.query.CustomerQueries;
import dev.ngb.core_billing.customer.application.customer.query.CustomerReadRepository;
import dev.ngb.core_billing.customer.application.customer.port.CustomerRepository;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.api.idempotency.IdempotencyStore;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.sharedkernel.Clock;
import dev.ngb.core_billing.sharedkernel.EventPublisher;
import dev.ngb.core_billing.sharedkernel.IdGenerator;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the customer module. Holds no business rules. */
@Configuration(proxyBeanMethods = false)
class CustomerConfiguration {

    @Bean
    CustomerRepository customerRepository(DSLContext dsl) {
        return new JooqCustomerRepository(dsl);
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
    ManageCustomerUseCase manageCustomer(
            UnitOfWork unitOfWork, CustomerRepository customers, AuditLog auditLog, TenantContext tenantContext, Clock clock) {
        return new ManageCustomerUseCase(unitOfWork, customers, auditLog, tenantContext, clock);
    }

    @Bean
    CustomerReadRepository customerReadRepository(DSLContext dsl) {
        return new JooqCustomerReadRepository(dsl);
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
