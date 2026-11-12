package dev.ngb.sampletenant.billing.adapter.client.http;

import java.time.Duration;

/** Everything the HTTP adapter needs to reach one Billing deployment. */
public record HttpBillingSettings(
        String baseUrl,
        String apiKey,
        Duration connectTimeout,
        Duration readTimeout,
        int maxAttempts,
        Duration retryBackoff) {}
