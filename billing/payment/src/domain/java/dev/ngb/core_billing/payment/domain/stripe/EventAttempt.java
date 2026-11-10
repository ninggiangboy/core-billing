package dev.ngb.core_billing.payment.domain.stripe;

import java.time.Instant;

/** One try at processing a stored event. {@code outcome} is one of PROCESSED, IGNORED, DEFERRED, FAILED, REJECTED. */
public record EventAttempt(int number, String outcome, String detail, Instant attemptedAt) {}
