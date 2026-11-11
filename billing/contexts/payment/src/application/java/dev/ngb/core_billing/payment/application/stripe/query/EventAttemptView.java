package dev.ngb.core_billing.payment.application.stripe.query;

import java.time.Instant;

/** One logged processing attempt of a Stripe event. */
public record EventAttemptView(int attempt, String outcome, String detail, Instant attemptedAt) {}
