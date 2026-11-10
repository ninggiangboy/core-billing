package io.corebilling.payment.application.payment.command;

import io.corebilling.payment.domain.stripe.StripeConfigurationId;
import io.corebilling.payment.domain.stripe.StripeEnvironment;
import io.corebilling.sharedkernel.Backoff;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Deployment settings of the payment module. The Connect settings are the Platform's own (its Stripe platform
 * account and Connect webhook secret) and exist only when the Platform offers Connect.
 */
public record PaymentSettings(
        String publicBaseUrl,
        Connect connect,
        String checkoutSuccessUrl,
        String checkoutCancelUrl,
        Duration refundHold,
        int eventMaxAttempts,
        Backoff eventBackoff,
        int eventBatchSize,
        Duration reconcileLookback) {

    public record Connect(String platformSecretKey, String webhookSecret, String onboardingReturnUrl, String onboardingRefreshUrl) {
        public Connect {
            Objects.requireNonNull(platformSecretKey, "platformSecretKey");
            Objects.requireNonNull(webhookSecret, "webhookSecret");
            Objects.requireNonNull(onboardingReturnUrl, "onboardingReturnUrl");
            Objects.requireNonNull(onboardingRefreshUrl, "onboardingRefreshUrl");
        }

        public StripeEnvironment environment() {
            return platformSecretKey.contains("_test_") ? StripeEnvironment.TEST : StripeEnvironment.LIVE;
        }

        /** Keeps the keys out of logs and exception messages. */
        @Override
        public String toString() {
            return "Connect[configured]";
        }
    }

    public PaymentSettings {
        Objects.requireNonNull(publicBaseUrl, "publicBaseUrl");
        Objects.requireNonNull(checkoutSuccessUrl, "checkoutSuccessUrl");
        Objects.requireNonNull(checkoutCancelUrl, "checkoutCancelUrl");
        Objects.requireNonNull(refundHold, "refundHold");
        Objects.requireNonNull(eventBackoff, "eventBackoff");
        Objects.requireNonNull(reconcileLookback, "reconcileLookback");
        if (eventMaxAttempts < 1 || eventBatchSize < 1) {
            throw new IllegalArgumentException("attempt and batch limits must be at least 1");
        }
        publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }

    public Optional<Connect> connectSettings() {
        return Optional.ofNullable(connect);
    }

    /** Where a Tenant points its Stripe webhook endpoint (Direct mode). */
    public String directWebhookUrl(StripeConfigurationId id) {
        return publicBaseUrl + "/stripe/webhooks/direct/" + id;
    }

    /** Where the Platform points its Stripe Connect webhook endpoint. */
    public String connectWebhookUrl() {
        return publicBaseUrl + "/stripe/webhooks/connect";
    }
}
