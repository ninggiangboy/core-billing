package dev.ngb.sampletenant.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.sampletenant.support.HttpIntegrationTest;
import dev.ngb.sampletenant.webhook.application.event.port.WebhookRepository;
import dev.ngb.sampletenant.webhook.domain.event.WebhookEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JooqWebhookRepositoryIntegrationTest extends HttpIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");

    @Autowired
    WebhookRepository events;

    @Autowired
    IdGenerator ids;

    private WebhookEvent received() {
        return WebhookEvent.received(
                ids, "evt_" + UUID.randomUUID(), "payment.failed", "tenant-1", NOW, "{\"id\":\"evt\"}", NOW);
    }

    @Test
    void theEventIdIsTheDeduplicationKey() {
        WebhookEvent event = received();

        assertThat(events.saveIfAbsent(event)).isTrue();
        assertThat(events.saveIfAbsent(event)).isFalse();
        assertThat(events.findByBillingEventId(event.billingEventId())).isPresent();
    }

    @Test
    void savingAProcessedEventUpdatesStatusDecisionAndDetail() {
        WebhookEvent event = received();
        events.saveIfAbsent(event);

        events.save(event.processed("RECORD_DEBT"));

        WebhookEvent loaded = events.findByBillingEventId(event.billingEventId()).orElseThrow();
        assertThat(loaded.status()).isEqualTo("PROCESSED");
        assertThat(loaded.decision()).isEqualTo("RECORD_DEBT");
    }
}
