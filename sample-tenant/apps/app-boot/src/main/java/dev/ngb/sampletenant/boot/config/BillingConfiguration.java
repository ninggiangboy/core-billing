package dev.ngb.sampletenant.boot.config;

import dev.ngb.sampletenant.billing.adapter.HttpBillingClient;
import dev.ngb.sampletenant.billing.adapter.HttpBillingSettings;
import dev.ngb.sampletenant.billing.api.BillingCharges;
import dev.ngb.sampletenant.billing.api.BillingCustomers;
import dev.ngb.sampletenant.billing.api.BillingInvoices;
import dev.ngb.sampletenant.billing.api.BillingPayments;
import dev.ngb.sampletenant.billing.api.BillingPricing;
import dev.ngb.sampletenant.billing.api.BillingSessions;
import dev.ngb.sampletenant.billing.api.BillingSubscriptions;
import dev.ngb.sampletenant.billing.api.BillingUsage;
import dev.ngb.sampletenant.billing.api.BillingWallets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One HTTP client for every call to Billing. The holder keeps that client out of the bean type graph,
 * so injecting one interface does not see the other interfaces the same object implements.
 */
@Configuration(proxyBeanMethods = false)
class BillingConfiguration {

    record BillingClientHolder(HttpBillingClient client) {}

    @Bean
    HttpBillingSettings httpBillingSettings(
            @Value("${sampletenant.billing.base-url}") String baseUrl,
            @Value("${sampletenant.billing.api-key:}") String apiKey,
            @Value("${sampletenant.billing.connect-timeout:PT3S}") Duration connectTimeout,
            @Value("${sampletenant.billing.read-timeout:PT10S}") Duration readTimeout,
            @Value("${sampletenant.billing.max-attempts:3}") int maxAttempts,
            @Value("${sampletenant.billing.retry-backoff:PT0.2S}") Duration retryBackoff) {
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
