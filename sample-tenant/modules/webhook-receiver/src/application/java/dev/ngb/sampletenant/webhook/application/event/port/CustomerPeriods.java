package dev.ngb.sampletenant.webhook.application.event.port;

import java.time.Instant;

/** The tenant's own service period, extended by a paid subscription renewal. */
public interface CustomerPeriods {

    void extend(String billingCustomerId, Instant periodEnd, Instant now);
}
