package dev.ngb.core_billing.webhook.domain.delivery;

import java.time.Instant;

/** What one try did: the status the receiver answered, or the reason there was none. */
public record DeliveryAttempt(int attemptNo, Integer statusCode, String error, String responseExcerpt, long latencyMs, Instant attemptedAt) {

    public boolean succeeded() {
        return statusCode != null && statusCode >= 200 && statusCode < 300;
    }
}
