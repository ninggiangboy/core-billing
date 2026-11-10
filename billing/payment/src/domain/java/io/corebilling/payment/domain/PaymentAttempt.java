package io.corebilling.payment.domain;

import java.time.Instant;

/** A card payment that did not go through; the customer may try again, so this is evidence and not an end state. */
public record PaymentAttempt(String stripeEventId, String failureCode, String failureMessage, Instant occurredAt) {}
