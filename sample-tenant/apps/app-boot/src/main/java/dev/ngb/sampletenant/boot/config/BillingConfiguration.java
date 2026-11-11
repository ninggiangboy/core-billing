package dev.ngb.sampletenant.boot.config;

import dev.ngb.sampletenant.billing.adapter.HttpBillingClient;
import dev.ngb.sampletenant.billing.adapter.HttpBillingSettings;
import dev.ngb.sampletenant.billing.api.BillingCustomers;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One HTTP client for every call to Billing; each interface is a view of the same connection pool. */
@Configuration(proxyBeanMethods = false)
class BillingConfiguration {

    @Bean
    HttpBillingSettings httpBillingSettings(
            @Value("${sampletenant.billing.base-url}") String baseUrl,
            @Value("${sampletenant.billing.api-key:}") String apiKey,
            @Value("${sampletenant.billing.connect-timeout:PT3S}") Duration connectTimeout,
            @Value("${sampletenant.billing.read-timeout:PT10S}") Duration readTimeout,
            @Value("${sampletenant.billing.max-attempts:3}") int maxAttempts,
            @Value("${sampletenant.billing.retry-backoff:PT200MS}") Duration retryBackoff) {
        return new HttpBillingSettings(baseUrl, apiKey, connectTimeout, readTimeout, maxAttempts, retryBackoff);
    }

    @Bean
    HttpBillingClient httpBillingClient(HttpBillingSettings settings) {
        return new HttpBillingClient(settings);
    }

    @Bean
    BillingCustomers billingCustomers(HttpBillingClient client) {
        return client;
    }
}
