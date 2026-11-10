package io.corebilling.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.corebilling.sharedkernel.TenantId;
import io.corebilling.webhook.domain.endpoint.EndpointStatus;
import io.corebilling.webhook.domain.endpoint.WebhookEndpoint;
import io.corebilling.webhook.domain.endpoint.WebhookEndpointId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WebhookEndpointTest {

    private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");

    private static WebhookEndpoint endpoint(List<String> types) {
        return WebhookEndpoint.create(new WebhookEndpointId(UUID.randomUUID()), new TenantId(UUID.randomUUID()), "https://hooks.example.com/x", "  my hook ", types,
                "whk_1", "cipher", NOW);
    }

    @Test
    void subscribesToTheTypesItNamed() {
        WebhookEndpoint e = endpoint(List.of("charge.succeeded", "reservation.created"));
        assertThat(e.subscribesTo("charge.succeeded")).isTrue();
        assertThat(e.subscribesTo("charge.failed")).isFalse();
        assertThat(e.description()).isEqualTo("my hook");
    }

    @Test
    void theWildcardSubscribesToEverything() {
        assertThat(endpoint(List.of("*")).subscribesTo("anything.at-all")).isTrue();
    }

    @Test
    void aDisabledEndpointReceivesNothing() {
        WebhookEndpoint e = endpoint(List.of("*"));
        e.changeStatus(EndpointStatus.DISABLED, NOW);
        assertThat(e.subscribesTo("charge.succeeded")).isFalse();
    }

    @Test
    void rotatingReplacesTheKeyAndTheCiphertext() {
        WebhookEndpoint e = endpoint(List.of("*"));
        e.rotateSecret("whk_2", "cipher-2", NOW.plusSeconds(5));
        assertThat(e.keyId()).isEqualTo("whk_2");
        assertThat(e.secretCiphertext()).isEqualTo("cipher-2");
        assertThat(e.secretRotatedAt()).isEqualTo(NOW.plusSeconds(5));
    }

    @Test
    void needsAtLeastOneEventType() {
        assertThatThrownBy(() -> endpoint(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
