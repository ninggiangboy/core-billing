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

    @Test
    void recognisesTheNamedUniqueConstraintAnywhereInTheChain() {
        SQLException violation = new SQLException(
                "duplicate key value violates unique constraint \"charges_tenant_id_idempotency_key_key\"", "23505");

        assertThat(Causes.violates(violation, "charges_tenant_id_idempotency_key_key")).isTrue();
        assertThat(Causes.violates(new IllegalStateException("outer", violation), "charges_tenant_id_idempotency_key_key")).isTrue();
    }

    @Test
    void aUniqueViolationOfAnotherConstraintOrStateDoesNotMatch() {
        SQLException violation = new SQLException(
                "duplicate key value violates unique constraint \"charges_tenant_id_idempotency_key_key\"", "23505");

        assertThat(Causes.violates(violation, "reservations_tenant_id_idempotency_key_key")).isFalse();
        assertThat(Causes.violates(new SQLException("same message but another state \"x\"", "40001"), "x")).isFalse();
        assertThat(Causes.violates(new IllegalStateException("broken"), "x")).isFalse();
    }
}
