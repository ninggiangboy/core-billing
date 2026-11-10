package dev.ngb.core_billing.payment.application.stripe.query;

import dev.ngb.core_billing.payment.domain.stripe.EventStatus;
import java.time.Instant;
import java.util.UUID;

/** A stored Stripe webhook event as the read API shows it. */
public record StripeEventView(
        UUID id,
        String eventId,
        String type,
        EventStatus status,
        int attempts,
        Instant nextAttemptAt,
        String lastError,
        Instant receivedAt,
        Instant processedAt) {}
