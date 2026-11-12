package dev.ngb.sampletenant.boot.config;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.sampletenant.billing.api.BillingCustomers;
import dev.ngb.sampletenant.billing.api.BillingInvoices;
import dev.ngb.sampletenant.billing.api.BillingPayments;
import dev.ngb.sampletenant.billing.api.BillingSessions;
import dev.ngb.sampletenant.billing.api.BillingWallets;
import dev.ngb.sampletenant.customer.api.CustomerDirectory;
import dev.ngb.sampletenant.customer.adapter.identity.CustomerAccountListener;
import dev.ngb.sampletenant.customer.adapter.persistence.CustomerJdbcRepository;
import dev.ngb.sampletenant.customer.adapter.persistence.JdbcCustomerRepository;
import dev.ngb.sampletenant.customer.adapter.persistence.JdbcWalletSettingRepository;
import dev.ngb.sampletenant.customer.application.billing.command.OpenBillingSessionUseCase;
import dev.ngb.sampletenant.customer.application.billing.command.TopUpUseCase;
import dev.ngb.sampletenant.customer.application.billing.query.CustomerBillingQueries;
import dev.ngb.sampletenant.customer.application.CustomerMapping;
import dev.ngb.sampletenant.customer.application.CustomerQueries;
import dev.ngb.sampletenant.customer.application.ManageWalletSettingUseCase;
import dev.ngb.sampletenant.customer.application.RegisterCustomerUseCase;
import dev.ngb.sampletenant.customer.application.port.CustomerRepository;
import dev.ngb.sampletenant.customer.application.port.WalletSettingRepository;
import dev.ngb.sampletenant.identity.api.account.AccountListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** Wires the customer mapping and the registration listener identity calls. */
@Configuration(proxyBeanMethods = false)
class CustomerConfiguration {

    @Bean
    CustomerRepository customerRepository(CustomerJdbcRepository rows, JdbcTemplate jdbc) {
        return new JdbcCustomerRepository(rows, jdbc);
    }

    @Bean
    CustomerMapping customerMapping(
            CustomerRepository customers,
            @Qualifier("billingCustomers") BillingCustomers billing,
            @Value("${sampletenant.billing.tenant-system-id:}") String tenantSystemId) {
        return new CustomerMapping(customers, billing, tenantSystemId);
    }

    @Bean
    RegisterCustomerUseCase registerCustomerUseCase(
            CustomerRepository customers, CustomerMapping mapping, Clock clock, IdGenerator ids) {
        return new RegisterCustomerUseCase(customers, mapping, clock, ids);
    }

    @Bean
    CustomerQueries customerQueries(CustomerRepository customers) {
        return new CustomerQueries(customers);
    }

    @Bean
    OpenBillingSessionUseCase openBillingSessionUseCase(
            CustomerDirectory customers,
            @Qualifier("billingSessions") BillingSessions sessions,
            @Value("${sampletenant.portal.return-url:}") String defaultReturnUrl) {
        return new OpenBillingSessionUseCase(customers, sessions, defaultReturnUrl);
    }

    @Bean
    TopUpUseCase topUpUseCase(
            CustomerDirectory customers,
            @Qualifier("billingWallets") BillingWallets wallets,
            @Qualifier("billingPayments") BillingPayments payments) {
        return new TopUpUseCase(customers, wallets, payments);
    }

    @Bean
    CustomerBillingQueries customerBillingQueries(
            CustomerDirectory customers,
            @Qualifier("billingInvoices") BillingInvoices invoices,
            @Qualifier("billingPayments") BillingPayments payments,
            @Qualifier("billingWallets") BillingWallets wallets) {
        return new CustomerBillingQueries(customers, invoices, payments, wallets);
    }

    @Bean
    WalletSettingRepository walletSettingRepository(JdbcTemplate jdbc) {
        return new JdbcWalletSettingRepository(jdbc);
    }

    @Bean
    ManageWalletSettingUseCase manageWalletSettingUseCase(
            CustomerQueries customers,
            WalletSettingRepository settings,
            @Qualifier("billingWallets") BillingWallets wallets,
            Clock clock) {
        return new ManageWalletSettingUseCase(customers, settings, wallets, clock);
    }

    @Bean
    AccountListener accountListener(RegisterCustomerUseCase register, ManageWalletSettingUseCase walletSettings) {
        return new CustomerAccountListener(register, walletSettings);
    }
}
