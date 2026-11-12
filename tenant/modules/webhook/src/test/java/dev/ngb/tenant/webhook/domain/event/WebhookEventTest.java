package dev.ngb.tenant.webhook.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ngb.core.runtime.IdGenerator;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WebhookEventTest {

    private static final UUID ID = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final UUID OTHER_ID = UUID.fromString("00000000-0000-7000-8000-000000000002");
    private static final Instant OCCURRED = Instant.parse("2026-11-11T00:00:00Z");
    private static final Instant RECEIVED = Instant.parse("2026-11-11T00:00:02Z");
    private static final IdGenerator IDS = () -> ID;

    private static WebhookEvent received() {
        return WebhookEvent.received(IDS, "evt_1", "invoice.paid", "ten_1", OCCURRED, "{}", RECEIVED);
    }

    @Test
    void aReceivedEventHasNoDecisionYet() {
        WebhookEvent event = received();

        assertThat(event.id()).isEqualTo(ID);
        assertThat(event.billingEventId()).isEqualTo("evt_1");
        assertThat(event.type()).isEqualTo("invoice.paid");
        assertThat(event.tenantId()).isEqualTo("ten_1");
        assertThat(event.occurredAt()).isEqualTo(OCCURRED);
        assertThat(event.receivedAt()).isEqualTo(RECEIVED);
        assertThat(event.status()).isEqualTo("RECEIVED");
        assertThat(event.decision()).isNull();
        assertThat(event.detail()).isNull();
        assertThat(event.pullEvents()).isEmpty();
    }

    @Test
    void processingRecordsTheDecisionOnANewInstanceAndLeavesTheOriginalAlone() {
        WebhookEvent event = received();

        WebhookEvent processed = event.processed("RESUMED");

        assertThat(processed.id()).isEqualTo(ID);
        assertThat(processed.status()).isEqualTo("PROCESSED");
        assertThat(processed.decision()).isEqualTo("RESUMED");
        assertThat(event.status()).isEqualTo("RECEIVED");
        assertThat(event.decision()).isNull();
    }

    @Test
    void failureRecordsTheReasonAndKeepsAnEarlierDecision() {
        WebhookEvent failed = received().processed("RESUMED").failed("database down");

        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.detail()).isEqualTo("database down");
        assertThat(failed.decision()).isEqualTo("RESUMED");
    }

    @Test
    void restoreRebuildsAStoredEventExactly() {
        WebhookEvent restored = WebhookEvent.restore(
                OTHER_ID, "evt_2", "invoice.failed", "ten_1", OCCURRED, "{\"a\":1}", RECEIVED, "FAILED", "SUSPENDED", "why");

        assertThat(restored.id()).isEqualTo(OTHER_ID);
        assertThat(restored.payload()).isEqualTo("{\"a\":1}");
        assertThat(restored.status()).isEqualTo("FAILED");
        assertThat(restored.decision()).isEqualTo("SUSPENDED");
        assertThat(restored.detail()).isEqualTo("why");
    }

    @Test
    void requiresTheFieldsTheReceiverRelaysOn() {
        assertThatThrownBy(() -> WebhookEvent.received(IDS, null, "t", "ten", OCCURRED, "{}", RECEIVED))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("billingEventId");
        assertThatThrownBy(() -> WebhookEvent.received(IDS, "evt", "t", "ten", OCCURRED, null, RECEIVED))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("payload");
    }

    @Test
    void twoInstancesAreTheSameEventWhenTheyShareAnId() {
        WebhookEvent event = received();

        assertThat(event.processed("RESUMED")).isEqualTo(event);
        assertThat(event.processed("RESUMED").hashCode()).isEqualTo(event.hashCode());
        assertThat(WebhookEvent.restore(
                        OTHER_ID, "evt_1", "invoice.paid", "ten_1", OCCURRED, "{}", RECEIVED, "RECEIVED", null, null))
                .isNotEqualTo(event);
    }
}
