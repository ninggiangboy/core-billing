package io.corebilling.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.corebilling.sharedkernel.Backoff;
import io.corebilling.webhook.domain.DeliveryPolicy;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class DeliveryPolicyTest {

    private final DeliveryPolicy policy = new DeliveryPolicy(3, new Backoff(Duration.ofSeconds(30), Duration.ofHours(6), 0.2), Duration.ofMinutes(2));

    @Test
    void theBudgetIsUsedUpAtTheLastAttempt() {
        assertThat(policy.exhausted(1)).isFalse();
        assertThat(policy.exhausted(2)).isFalse();
        assertThat(policy.exhausted(3)).isTrue();
    }

    @Test
    void retriesBackOffExponentially() {
        assertThat(policy.retryDelay(1, 0.5)).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.retryDelay(2, 0.5)).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void rejectsAnEmptyBudget() {
        assertThatThrownBy(() -> new DeliveryPolicy(0, policy.backoff(), Duration.ofMinutes(1))).isInstanceOf(IllegalArgumentException.class);
    }
}
