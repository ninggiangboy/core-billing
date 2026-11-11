package dev.ngb.sampletenant.webhook.domain;

import dev.ngb.sampletenant.shared.id.Ids;
import java.time.Instant;
import java.util.UUID;

/** One received Billing event, kept so a repeat delivery is recognised and the decision is auditable. */
public record WebhookEvent(
        UUID id,
        String billingEventId,
        String type,
        String tenantId,
        Instant occurredAt,
        String payload,
        Instant receivedAt,
        String status,
        String decision,
        String detail) {

    public static WebhookEvent received(
            String billingEventId, String type, String tenantId, Instant occurredAt, String payload, Instant receivedAt) {
        return new WebhookEvent(
                Ids.newId(), billingEventId, type, tenantId, occurredAt, payload, receivedAt, "RECEIVED", null, null);
    }

    public WebhookEvent processed(String nextDecision) {
        return new WebhookEvent(
                id, billingEventId, type, tenantId, occurredAt, payload, receivedAt, "PROCESSED", nextDecision, detail);
    }

    public WebhookEvent failed(String reason) {
        return new WebhookEvent(
                id, billingEventId, type, tenantId, occurredAt, payload, receivedAt, "FAILED", decision, reason);
    }
}
