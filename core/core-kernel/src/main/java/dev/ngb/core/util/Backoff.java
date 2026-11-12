package dev.ngb.core.util;

import java.time.Duration;
import java.util.Objects;

/**
 * Exponential delay between retries with jitter: {@code base * 2^(failures-1)}, capped, then spread by
 * +/- {@code jitter} so retries of many rows do not line up.
 */
public record Backoff(Duration base, Duration cap, double jitter) {

    public Backoff {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(cap, "cap");
        if (base.isNegative() || base.isZero() || cap.compareTo(base) < 0) {
            throw new IllegalArgumentException("base must be positive and not above the cap");
        }
        if (jitter < 0 || jitter >= 1) {
            throw new IllegalArgumentException("jitter must be in [0, 1)");
        }
    }

    /**
     * @param failures how many attempts have failed so far (at least 1)
     * @param random a value in [0, 1) that positions the delay inside the jitter band
     */
    public Duration delayAfter(int failures, double random) {
        if (failures < 1) {
            throw new IllegalArgumentException("failures must be at least 1");
        }
        double exponential = base.toMillis() * Math.pow(2, Math.min(failures - 1, 40));
        double capped = Math.min(exponential, cap.toMillis());
        double spread = 1 + jitter * (2 * random - 1);
        return Duration.ofMillis(Math.max(1, Math.round(capped * spread)));
    }
}
