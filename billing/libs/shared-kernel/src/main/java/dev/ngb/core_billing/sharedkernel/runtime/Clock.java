package dev.ngb.core_billing.sharedkernel.runtime;

import java.time.Instant;

/** Port for the current instant, so domain code never reads the system clock directly. */
public interface Clock {

    Instant now();
}
