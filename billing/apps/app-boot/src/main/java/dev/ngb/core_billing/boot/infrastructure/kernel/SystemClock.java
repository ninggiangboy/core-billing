package dev.ngb.core_billing.boot.infrastructure.kernel;

import dev.ngb.core_billing.sharedkernel.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Wall-clock time at microsecond precision, which is what PostgreSQL stores, so reloads compare equal. */
public class SystemClock implements Clock {

    @Override
    public Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
