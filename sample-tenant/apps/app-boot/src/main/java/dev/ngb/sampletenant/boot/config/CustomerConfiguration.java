package dev.ngb.sampletenant.boot.config;

import dev.ngb.sampletenant.billing.api.BillingCustomers;
import dev.ngb.sampletenant.billing.api.BillingInvoices;
import dev.ngb.sampletenant.billing.api.BillingPayments;
import dev.ngb.sampletenant.billing.api.BillingSessions;
import dev.ngb.sampletenant.billing.api.BillingWallets;
import dev.ngb.sampletenant.customer.adapter.identity.CustomerAccountListener;
import dev.ngb.sampletenant.customer.adapter.persistence.CustomerJdbcRepository;
import dev.ngb.sampletenant.customer.adapter.persistence.JdbcCustomerRepository;
import dev.ngb.sampletenant.customer.adapter.persistence.JdbcWalletSettingRepository;
import dev.ngb.sampletenant.customer.application.CustomerBillingViews;
import dev.ngb.sampletenant.customer.application.CustomerMapping;
import dev.ngb.sampletenant.customer.application.CustomerQueries;
import dev.ngb.sampletenant.customer.application.ManageWalletSettingUseCase;
import dev.ngb.sampletenant.customer.application.RegisterCustomerUseCase;
import dev.ngb.sampletenant.customer.application.port.CustomerRepository;
import dev.ngb.sampletenant.customer.application.port.WalletSettingRepository;
import dev.ngb.sampletenant.identity.application.port.AccountListener;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

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
    CustomerBillingViews customerBillingViews(
            CustomerQueries customers,
            BillingSessions sessions,
            BillingInvoices invoices,
            BillingPayments payments,
            BillingWallets wallets,
            @Value("${sampletenant.portal.return-url:}") String defaultReturnUrl) {
        return new CustomerBillingViews(customers, sessions, invoices, payments, wallets, defaultReturnUrl);
    }

    @Bean
    WalletSettingRepository walletSettingRepository(JdbcTemplate jdbc) {
        return new JdbcWalletSettingRepository(jdbc);
    }

    @Bean
    ManageWalletSettingUseCase manageWalletSettingUseCase(
            CustomerQueries customers, WalletSettingRepository settings, BillingWallets wallets, Clock clock) {
        return new ManageWalletSettingUseCase(customers, settings, wallets, clock);
    }

    @Bean
    AccountListener accountListener(RegisterCustomerUseCase register, ManageWalletSettingUseCase walletSettings) {
        return new CustomerAccountListener(register, walletSettings);
    }
}
