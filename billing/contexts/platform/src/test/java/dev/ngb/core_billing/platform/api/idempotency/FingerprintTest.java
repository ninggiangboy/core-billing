package dev.ngb.core_billing.platform.api.idempotency;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ngb.core_billing.platform.api.error.IdempotencyConflictException;
import org.junit.jupiter.api.Test;

class FingerprintTest {

    @Test
    void theSameRequestHashIsAccepted() {
        assertThatCode(() -> Fingerprint.requireSameRequest("charge", "key-1", "hash-a", "hash-a"))
                .doesNotThrowAnyException();
    }

    @Test
    void aDifferentRequestHashIsAnIdempotencyConflict() {
        assertThatThrownBy(() -> Fingerprint.requireSameRequest("charge", "key-1", "hash-a", "hash-b"))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("charge/key-1");
    }
}
