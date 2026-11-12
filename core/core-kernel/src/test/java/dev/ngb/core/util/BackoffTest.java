package dev.ngb.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class BackoffTest {

    private final Backoff backoff = new Backoff(Duration.ofSeconds(10), Duration.ofMinutes(5), 0.2);

    @Test
    void doublesUntilTheCap() {
        assertThat(backoff.delayAfter(1, 0.5)).isEqualTo(Duration.ofSeconds(10));
        assertThat(backoff.delayAfter(2, 0.5)).isEqualTo(Duration.ofSeconds(20));
        assertThat(backoff.delayAfter(3, 0.5)).isEqualTo(Duration.ofSeconds(40));
        assertThat(backoff.delayAfter(30, 0.5)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void jitterStaysInsideTheBand() {
        assertThat(backoff.delayAfter(1, 0.0)).isEqualTo(Duration.ofSeconds(8));
        assertThat(backoff.delayAfter(1, 0.999999)).isBetween(Duration.ofMillis(11_990), Duration.ofSeconds(12));
    }

    @Test
    void rejectsNonsense() {
        assertThatThrownBy(() -> backoff.delayAfter(0, 0.5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Backoff(Duration.ofMinutes(1), Duration.ofSeconds(1), 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Backoff(Duration.ofSeconds(1), Duration.ofSeconds(2), 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
