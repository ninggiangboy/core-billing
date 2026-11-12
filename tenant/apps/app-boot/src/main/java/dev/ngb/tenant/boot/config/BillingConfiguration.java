package dev.ngb.tenant.boot.config;

import dev.ngb.tenant.billing.adapter.client.http.HttpBillingClient;
import dev.ngb.tenant.billing.adapter.client.http.HttpBillingSettings;
import dev.ngb.tenant.billing.api.client.BillingCharges;
import dev.ngb.tenant.billing.api.client.BillingCustomers;
import dev.ngb.tenant.billing.api.client.BillingInvoices;
import dev.ngb.tenant.billing.api.client.BillingPayments;
import dev.ngb.tenant.billing.api.client.BillingPricing;
import dev.ngb.tenant.billing.api.client.BillingSessions;
import dev.ngb.tenant.billing.api.client.BillingSubscriptions;
import dev.ngb.tenant.billing.api.client.BillingUsage;
import dev.ngb.tenant.billing.api.client.BillingWallets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * One HTTP client for every call to Billing. The holder keeps that client out of the bean type graph,
 * so injecting one interface does not see the other interfaces the same object implements.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!test")
class BillingConfiguration {

    record BillingClientHolder(HttpBillingClient client) {}

    @Bean
    HttpBillingSettings httpBillingSettings(
            @Value("${tenant.billing.base-url}") String baseUrl,
            @Value("${tenant.billing.api-key:}") String apiKey,
            @Value("${tenant.billing.connect-timeout:PT3S}") Duration connectTimeout,
            @Value("${tenant.billing.read-timeout:PT10S}") Duration readTimeout,
            @Value("${tenant.billing.max-attempts:3}") int maxAttempts,
            @Value("${tenant.billing.retry-backoff:PT0.2S}") Duration retryBackoff) {
        return new HttpBillingSettings(baseUrl, apiKey, connectTimeout, readTimeout, maxAttempts, retryBackoff);
    }

    @Bean
    BillingClientHolder billingClientHolder(HttpBillingSettings settings) {
        return new BillingClientHolder(new HttpBillingClient(settings));
    }

    @Bean
    BillingCustomers billingCustomers(BillingClientHolder holder) {
        return holder.client();
    }

    @Bean
    BillingPricing billingPricing(BillingClientHolder holder) {
        return holder.client();
    }

    @Bean
    BillingWallets billingWallets(BillingClientHolder holder) {
        return holder.client();
    }

    @Bean
    BillingCharges billingCharges(BillingClientHolder holder) {
        return holder.client();
    }

    @Bean
    BillingUsage billingUsage(BillingClientHolder holder) {
        return holder.client();
    }

    @Bean
    BillingSessions billingSessions(BillingClientHolder holder) {
        return holder.client();
    }

    @Bean
    BillingPayments billingPayments(BillingClientHolder holder) {
        return holder.client();
    }

    @Bean
    BillingInvoices billingInvoices(BillingClientHolder holder) {
        return holder.client();
    }

    @Bean
    BillingSubscriptions billingSubscriptions(BillingClientHolder holder) {
        return holder.client();
    }
}
