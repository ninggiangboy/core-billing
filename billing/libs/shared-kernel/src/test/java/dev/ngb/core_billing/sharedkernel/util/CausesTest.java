package dev.ngb.core_billing.sharedkernel.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class CausesTest {

    @Test
    void recognisesALockTimeoutOrDeadlockAnywhereInTheChain() {
        assertThat(Causes.isRetryableContention(new SQLException("lock timeout", "55P03"))).isTrue();
        assertThat(Causes.isRetryableContention(new IllegalStateException("outer", new SQLException("deadlock", "40P01")))).isTrue();
    }

    @Test
    void leavesEveryOtherFailureAlone() {
        assertThat(Causes.isRetryableContention(new SQLException("duplicate key", "23505"))).isFalse();
        assertThat(Causes.isRetryableContention(new SQLException("no sql state"))).as("a null state is not a match").isFalse();
        assertThat(Causes.isRetryableContention(new IllegalStateException("broken"))).isFalse();
    }
}
