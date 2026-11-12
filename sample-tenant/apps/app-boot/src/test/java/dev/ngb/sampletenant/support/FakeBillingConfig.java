package dev.ngb.sampletenant.support;

import dev.ngb.sampletenant.billing.api.client.BillingAdmin;
import dev.ngb.sampletenant.billing.api.client.BillingCharges;
import dev.ngb.sampletenant.billing.api.client.BillingCustomers;
import dev.ngb.sampletenant.billing.api.client.BillingInvoices;
import dev.ngb.sampletenant.billing.api.client.BillingPayments;
import dev.ngb.sampletenant.billing.api.client.BillingPricing;
import dev.ngb.sampletenant.billing.api.client.BillingSessions;
import dev.ngb.sampletenant.billing.api.client.BillingSubscriptions;
import dev.ngb.sampletenant.billing.api.client.BillingUsage;
import dev.ngb.sampletenant.billing.api.client.BillingWallets;
import dev.ngb.sampletenant.billing.api.client.BillingWebhooks;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces every Billing client bean with one in-memory fake; no test ever calls the network. The
 * HTTP tests run with the {@code test} profile, which switches off {@code BillingConfiguration}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FakeBillingConfig {

    private final FakeBilling billing = new FakeBilling();

    @Bean
    FakeBilling fakeBilling() {
        return billing;
    }

    @Bean
    @Primary
    BillingAdmin billingAdmin() {
        return billing;
    }

    @Bean
    @Primary
    BillingCharges billingCharges() {
        return billing;
    }

    @Bean
    @Primary
    BillingCustomers billingCustomers() {
        return billing;
    }

    @Bean
    @Primary
    BillingInvoices billingInvoices() {
        return billing;
    }

    @Bean
    @Primary
    BillingPayments billingPayments() {
        return billing;
    }

    @Bean
    @Primary
    BillingPricing billingPricing() {
        return billing;
    }

    @Bean
    @Primary
    BillingSessions billingSessions() {
        return billing;
    }

    @Bean
    @Primary
    BillingSubscriptions billingSubscriptions() {
        return billing;
    }

    @Bean
    @Primary
    BillingUsage billingUsage() {
        return billing;
    }

    @Bean
    @Primary
    BillingWallets billingWallets() {
        return billing;
    }

    @Bean
    @Primary
    BillingWebhooks billingWebhooks() {
        return billing;
    }
}
