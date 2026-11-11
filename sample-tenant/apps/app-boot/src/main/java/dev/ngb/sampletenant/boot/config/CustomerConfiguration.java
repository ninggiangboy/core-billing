package dev.ngb.sampletenant.boot.config;

import dev.ngb.sampletenant.billing.api.BillingCustomers;
import dev.ngb.sampletenant.customer.adapter.identity.CustomerAccountListener;
import dev.ngb.sampletenant.customer.adapter.persistence.CustomerJdbcRepository;
import dev.ngb.sampletenant.customer.adapter.persistence.JdbcCustomerRepository;
import dev.ngb.sampletenant.customer.application.CustomerMapping;
import dev.ngb.sampletenant.customer.application.CustomerQueries;
import dev.ngb.sampletenant.customer.application.RegisterCustomerUseCase;
import dev.ngb.sampletenant.customer.application.port.CustomerRepository;
import dev.ngb.sampletenant.identity.application.port.AccountListener;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the customer mapping and the registration listener identity calls. */
@Configuration(proxyBeanMethods = false)
class CustomerConfiguration {

    @Bean
    CustomerRepository customerRepository(CustomerJdbcRepository rows) {
        return new JdbcCustomerRepository(rows);
    }

    @Bean
    CustomerMapping customerMapping(
            CustomerRepository customers,
            BillingCustomers billing,
            @Value("${sampletenant.billing.tenant-system-id:}") String tenantSystemId) {
        return new CustomerMapping(customers, billing, tenantSystemId);
    }

    @Bean
    RegisterCustomerUseCase registerCustomerUseCase(
            CustomerRepository customers, CustomerMapping mapping, Clock clock) {
        return new RegisterCustomerUseCase(customers, mapping, clock);
    }

    @Bean
    CustomerQueries customerQueries(CustomerRepository customers) {
        return new CustomerQueries(customers);
    }

    @Bean
    AccountListener accountListener(RegisterCustomerUseCase register) {
        return new CustomerAccountListener(register);
    }
}
