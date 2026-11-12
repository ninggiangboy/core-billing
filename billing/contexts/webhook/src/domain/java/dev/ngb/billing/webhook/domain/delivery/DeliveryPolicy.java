package dev.ngb.billing.webhook.domain.delivery;

import dev.ngb.core.util.Backoff;
import java.time.Duration;
import java.util.Objects;

/** How often a delivery is tried, how long between tries, and how long a worker may hold one. */
public record DeliveryPolicy(int maxAttempts, Backoff backoff, Duration lease) {

    public DeliveryPolicy {
        Objects.requireNonNull(backoff, "backoff");
        Objects.requireNonNull(lease, "lease");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        if (lease.isNegative() || lease.isZero()) {
            throw new IllegalArgumentException("lease must be positive");
        }
    }

    /** True when {@code attemptsMade} attempts have used up the budget. */
    public boolean exhausted(int attemptsMade) {
        return attemptsMade >= maxAttempts;
    }

    public Duration retryDelay(int attemptsMade, double random) {
        return backoff.delayAfter(attemptsMade, random);
    }
}
