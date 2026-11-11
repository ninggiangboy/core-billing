package dev.ngb.core_billing.session.application.portal.service;

import java.time.Duration;
import java.util.Objects;

/** Deployment limits for Billing Sessions. A caller may choose any lifetime between the minimum and the maximum. */
public record SessionSettings(Duration defaultTtl, Duration minTtl, Duration maxTtl) {

    public SessionSettings {
        Objects.requireNonNull(defaultTtl, "defaultTtl");
        Objects.requireNonNull(minTtl, "minTtl");
        Objects.requireNonNull(maxTtl, "maxTtl");
        if (minTtl.isNegative() || minTtl.isZero() || maxTtl.compareTo(minTtl) < 0 || defaultTtl.compareTo(minTtl) < 0
                || defaultTtl.compareTo(maxTtl) > 0) {
            throw new IllegalArgumentException("session lifetimes must satisfy 0 < min <= default <= max");
        }
    }
}
