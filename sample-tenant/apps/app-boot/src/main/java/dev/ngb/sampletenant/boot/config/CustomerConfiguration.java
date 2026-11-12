package dev.ngb.sampletenant.boot.config;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.sampletenant.billing.api.client.BillingCustomers;
import dev.ngb.sampletenant.billing.api.client.BillingInvoices;
import dev.ngb.sampletenant.billing.api.client.BillingPayments;
import dev.ngb.sampletenant.billing.api.client.BillingSessions;
import dev.ngb.sampletenant.billing.api.client.BillingWallets;
import dev.ngb.sampletenant.customer.adapter.customer.identity.CustomerAccountListener;
import dev.ngb.sampletenant.customer.adapter.customer.persistence.JooqCustomerReadRepository;
import dev.ngb.sampletenant.customer.adapter.customer.persistence.JooqCustomerRepository;
import dev.ngb.sampletenant.customer.adapter.wallet.persistence.JooqWalletSettingRepository;
import dev.ngb.sampletenant.customer.api.lookup.CustomerDirectory;
import dev.ngb.sampletenant.customer.application.billing.command.OpenBillingSessionUseCase;
import dev.ngb.sampletenant.customer.application.billing.command.TopUpUseCase;
import dev.ngb.sampletenant.customer.application.billing.query.CustomerBillingQueries;
import dev.ngb.sampletenant.customer.application.customer.command.RegisterCustomerUseCase;
import dev.ngb.sampletenant.customer.application.customer.port.CustomerRepository;
import dev.ngb.sampletenant.customer.application.customer.query.CustomerQueries;
import dev.ngb.sampletenant.customer.application.customer.query.CustomerReadRepository;
import dev.ngb.sampletenant.customer.application.customer.service.CustomerDirectoryService;
import dev.ngb.sampletenant.customer.application.customer.service.CustomerMapping;
import dev.ngb.sampletenant.customer.application.wallet.command.ManageWalletSettingUseCase;
import dev.ngb.sampletenant.customer.application.wallet.port.WalletSettingRepository;
import dev.ngb.sampletenant.identity.api.account.AccountListener;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the customer mapping and the registration listener identity calls. */
@Configuration(proxyBeanMethods = false)
class CustomerConfiguration {

    @Bean
    CustomerRepository customerRepository(DSLContext dsl) {
        return new JooqCustomerRepository(dsl);
    }

    @Bean
    CustomerReadRepository customerReadRepository(DSLContext dsl) {
        return new JooqCustomerReadRepository(dsl);
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
    CustomerQueries customerQueries(CustomerReadRepository customers) {
        return new CustomerQueries(customers);
    }

    @Bean
    CustomerDirectoryService customerDirectoryService(CustomerReadRepository customers) {
        return new CustomerDirectoryService(customers);
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
    WalletSettingRepository walletSettingRepository(DSLContext dsl) {
        return new JooqWalletSettingRepository(dsl);
    }

    @Bean
    ManageWalletSettingUseCase manageWalletSettingUseCase(
            CustomerDirectory customers,
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
