package dev.ngb.core_billing.payment.application.payment.query;

import java.time.Instant;

/** A failed card attempt recorded on a payment. */
public record PaymentAttemptView(String failureCode, String failureMessage, Instant occurredAt) {}
