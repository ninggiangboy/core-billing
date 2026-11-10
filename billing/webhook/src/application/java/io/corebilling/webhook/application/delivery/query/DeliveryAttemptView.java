package io.corebilling.webhook.application.delivery.query;

import java.time.Instant;

/** One logged attempt of a delivery. */
public record DeliveryAttemptView(int attemptNo, Integer statusCode, String error, String responseExcerpt, long latencyMs, Instant attemptedAt) {}
