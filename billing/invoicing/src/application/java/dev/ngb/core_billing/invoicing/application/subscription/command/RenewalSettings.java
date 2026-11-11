package dev.ngb.core_billing.invoicing.application.subscription.command;

import java.time.Duration;

/** How many due subscriptions one job run renews, and how long a failed renewal waits before it is tried again. */
public record RenewalSettings(int batchSize, Duration retryDelay) {

    public RenewalSettings {
        if (batchSize < 1 || retryDelay == null || retryDelay.isNegative() || retryDelay.isZero()) {
            throw new IllegalArgumentException("a positive batch size and retry delay are required");
        }
    }
}
