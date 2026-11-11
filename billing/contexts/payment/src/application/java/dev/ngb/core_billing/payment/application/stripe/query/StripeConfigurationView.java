package dev.ngb.core_billing.payment.application.stripe.query;

import dev.ngb.core_billing.payment.domain.stripe.ConnectionStatus;
import dev.ngb.core_billing.payment.domain.stripe.IntegrationMode;
import dev.ngb.core_billing.payment.domain.stripe.StripeEnvironment;
import java.time.Instant;
import java.util.UUID;

/** The current Stripe configuration as the GET handler shows it; it never carries a secret. */
public record StripeConfigurationView(
        UUID id,
        IntegrationMode mode,
        String accountId,
        StripeEnvironment environment,
        ConnectionStatus status,
        String publishableKey,
        boolean webhookSecretSet,
        boolean cardPaymentsActive,
        Instant createdAt,
        Instant updatedAt,
        Instant connectedAt,
        Instant lastVerifiedAt) {}
