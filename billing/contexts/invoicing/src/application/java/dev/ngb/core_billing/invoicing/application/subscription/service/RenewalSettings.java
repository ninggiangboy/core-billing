package dev.ngb.core_billing.invoicing.application.subscription.service;

import dev.ngb.core.util.Backoff;
import java.util.Objects;

/**
 * How many due subscriptions one job run renews, and the bounded schedule for a period that cannot be paid: after
 * each failed attempt the next one waits longer (capped), and after {@code maxAttempts} Billing stops trying on its
 * own and leaves the subscription past due for the Tenant System to decide.
 */
public record RenewalSettings(int batchSize, int maxAttempts, Backoff backoff) {

    public RenewalSettings {
        Objects.requireNonNull(backoff, "backoff");
        if (batchSize < 1 || maxAttempts < 1) {
            throw new IllegalArgumentException("a positive batch size and attempt limit are required");
        }
    }
}
