package io.corebilling.sharedkernel;

import java.time.Instant;

/** Port for the current instant, so domain code never reads the system clock directly. */
public interface Clock {

    Instant now();
}
